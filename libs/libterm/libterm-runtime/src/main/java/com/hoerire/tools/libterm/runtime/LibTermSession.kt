package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.OutputChunk
import com.hoerire.tools.libterm.SendResult
import com.hoerire.tools.libterm.SessionState
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.TerminalSession
import com.hoerire.tools.libterm.runtime.internal.LibTermSessionOutputPipeline
import com.hoerire.tools.libterm.runtime.internal.TerminalCommandExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibTermSession internal constructor(
    private val session: TerminalSession,
    private val closeSession: suspend (String) -> Boolean,
    decoder: SessionTerminalOutputDecoder,
) {
    private val inputMutex = Mutex()
    private val outputPipeline = LibTermSessionOutputPipeline(
        session = session,
        decoder = decoder,
    )

    val id: String = session.id
    val identity: TerminalIdentity = session.identity
    val state: StateFlow<SessionState> = session.state
    val stream: Flow<TerminalTextChunk> = outputPipeline.stream

    /** 原始 PTY 字节流（未剥 ANSI），给块内 VT 渲染器用。 */
    val output: Flow<OutputChunk> = session.output

    fun latest(limit: Int): List<TerminalTextChunk> = outputPipeline.latest(limit)

    /**
     * 原始 PTY 字节回放（补种用）：把会话缓冲里现存的原始块按序拼起来，只保留末尾 [maxBytes] 字节。
     *
     * [stream] 走的是 decoder 剥过 ANSI 的文本层，颜色 / 光标序列在那一步就丢了；
     * 块内 VT 渲染器要复现颜色，必须喂这一份原始字节。
     */
    fun rawSnapshot(maxBytes: Int = RAW_SNAPSHOT_MAX_BYTES): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        val chunks = session.latest(Int.MAX_VALUE)
        if (chunks.isEmpty()) return ByteArray(0)
        var total = 0
        var from = chunks.size
        while (from > 0 && total + chunks[from - 1].bytes.size <= maxBytes) {
            from -= 1
            total += chunks[from].bytes.size
        }
        if (total == 0) {
            // 单块就超限：只留最后 maxBytes 字节（末尾才是屏上还看得见的部分）
            val last = chunks.last().bytes.toByteArray()
            return if (last.size <= maxBytes) last else last.copyOfRange(last.size - maxBytes, last.size)
        }
        val out = ByteArray(total)
        var offset = 0
        for (index in from until chunks.size) {
            val bytes = chunks[index].bytes.toByteArray()
            bytes.copyInto(out, offset)
            offset += bytes.size
        }
        return out
    }

    companion object {
        /** 补种上限：够覆盖块晚进组合的那几秒，又不至于把整段历史灌进屏。 */
        const val RAW_SNAPSHOT_MAX_BYTES: Int = 32_768
    }

    internal fun dispose() {
        outputPipeline.close()
    }

    suspend fun exec(
        command: String,
        timeoutMillis: Long = Term.DEFAULT_EXEC_TIMEOUT_MILLIS,
    ): TermResult<CommandResult> {
        return inputMutex.withLock {
            TerminalCommandExecutor.exec(
                session = session,
                command = command,
                timeoutMillis = timeoutMillis,
            )
        }
    }

    suspend fun write(text: String): TermResult<Unit> {
        return write(text.encodeToByteArray())
    }

    suspend fun write(bytes: ByteArray): TermResult<Unit> {
        return inputMutex.withLock {
            when (val result = session.send(bytes)) {
                SendResult.Sent -> TermResult.Success(Unit)
                is SendResult.Failed -> TermResult.Failure(result.failure)
            }
        }
    }

    suspend fun close(): TermResult<Unit> {
        return if (closeSession(id)) {
            dispose()
            TermResult.Success(Unit)
        } else {
            TermResult.Failure(
                TerminalFailure.AlreadyClosed(
                    identity = identity,
                    message = "Session is already closed",
                ),
            )
        }
    }
}
