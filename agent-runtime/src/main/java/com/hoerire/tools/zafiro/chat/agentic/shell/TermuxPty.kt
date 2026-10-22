package com.hoerire.tools.zafiro.chat.agentic.shell

import android.content.Context
import android.util.Log
import com.hoerire.tools.logging.Logger
import com.hoerire.tools.libterm.OpenResult
import com.hoerire.tools.libterm.TerminalBytes
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.runtime.CommandResult
import com.hoerire.tools.libterm.runtime.TermResult
import com.hoerire.tools.libterm.runtime.TerminalTextChunk
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** A 方案：多条真实 PTY 会话，页面与工具共用（工具用当前活跃那条）。 */
object TermuxPty {
    private const val TAG = "TermuxPty"

    /**
     * 工具侧所需的最小面：建会话 / 取当前 / 关会话。
     *
     * 顶栏终端页已删（HANDOFF-zterm3 §9.2），对应的 UI 订阅 API（uiSession、addScreenListener、
     * sessions、setActive 等）一并移除——它们没有调用者，且 pushDelta 每次都要空跑一遍 listener。
     * 活跃会话恒等于最后创建的那条（原 activeId 由 UI 的 setActive 维护）。
     */
    /**
     * 清掉上一条命的 PTY 子进程。
     *
     * App 被强杀 / 崩溃时 TermuxPtyPort.close() 不会执行，sh 会挂到 init(1) 下变孤儿
     * （实测残留过 813 PPID=1）。新进程启动时**所有**已有 PTY 必然是孤儿，按环境变量
     * 标记认人打死即可；root 会话的 environ 本进程读不到，这里跳过。
     */
    private fun sweepOrphans() {
        val selfPid = android.os.Process.myPid()
        val procs = File("/proc").listFiles() ?: return
        var killed = 0
        for (dir in procs) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid == selfPid) continue
            // 只认「真孤儿」：PPID==1。否则多进程/多会话场景会误杀别的进程的活会话。
            if (!isOrphan(pid)) continue
            val environ = runCatching { File(dir, "environ").readBytes() }.getOrNull() ?: continue
            if (!environ.toString(Charsets.UTF_8).contains(PTY_MARKER)) continue
            if (runCatching { android.os.Process.sendSignal(pid, 9) }.isSuccess) killed++
        }
        if (killed > 0) Logger.i(TAG, "sweep orphan ptys killed=" + killed)
    }

    /** /proc/<pid>/stat 的 ppid 是否为 1（被 init 收养）。comm 可能带空格括号，按最后一个 ')' 切。 */
    private fun isOrphan(pid: Int): Boolean = runCatching {
        val stat = File("/proc/" + pid + "/stat").readText()
        stat.substringAfterLast(')').trim().split(' ').getOrNull(1)?.toInt() == 1
    }.getOrDefault(false)

    private const val PTY_MARKER = "ZAFIRO_PTY=1"

    private val ports = LinkedHashMap<String, TermuxPtyPort>()
    private var seq = 0

    init { sweepOrphans() }

    internal fun activePort(): TermuxPtyPort? = synchronized(ports) { ports.values.lastOrNull() }

    internal fun closeSession(id: String) {
        val port = synchronized(ports) { ports.remove(id) }
        runCatching { port?.session?.finishIfRunning() }
    }

    internal suspend fun open(
        identity: TerminalIdentity,
        cwd: String?,
        contextProvider: suspend () -> Context,
    ): OpenResult<TerminalSessionPort> {
        // 复用只认同 identity：否则 user 的空闲会话会被 root 工具拿去跑（跨权限串台）。
        synchronized(ports) { ports.values.lastOrNull { it.identity == identity && it.isAlive } }
            ?.let { return OpenResult.Success(it) }
        return runCatching { createSession(contextProvider(), identity) }.fold(
            onSuccess = { p -> OpenResult.Success(p) },
            onFailure = { OpenResult.Failure(TerminalFailure.StartupFailed(identity, it.message, it)) },
        )
    }

    private fun createSession(context: Context, identity: TerminalIdentity): TermuxPtyPort =
        synchronized(ports) {
            val p = build(context, "s${++seq}", identity)
            ports[p.id] = p
            p
        }

    private fun build(context: Context, sid: String, identity: TerminalIdentity): TermuxPtyPort {
        val home = File(context.filesDir, "termux-home").apply { mkdirs() }.absolutePath
        // 真 PTY 下 su 才有 tty 可用：早先 `su -c sh` 挂住是因为当时根本没有 PTY
        // （initializeEmulator 没调），不是 su 本身的问题。现在 forkpty → exec su。
        val cmd = if (identity == TerminalIdentity.Su) "/system/bin/su" else "/system/bin/sh"
        val args = arrayOf(cmd)
        val pwd = if (identity == TerminalIdentity.Su) "/" else home
        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=$home",
            "PWD=$pwd",
            "PATH=/system/bin:/system/xbin:/sbin",
            "LANG=en_US.UTF-8",
            // 孤儿兜底用：App 被强杀时 finally 不跑，子进程会挂到 init 下变孤儿。
            // 启动时靠这个标记（在 /proc/<pid>/environ 里）把它们认出来打死。
            "ZAFIRO_PTY=1",
        )
        Logger.i(TAG, "create $sid identity=$identity cmd=$cmd exists=${File(cmd).exists()}")
        return TermuxPtyPort.create(sid, context, cmd, pwd, args, env, identity)
    }
}

internal class TermuxPtyPort(
    private val sid: String,
    val session: TerminalSession,
    private val chunks: MutableSharedFlow<TerminalTextChunk>,
    val identity: TerminalIdentity,
) : TerminalSessionPort {
    override val id: String get() = sid
    /** 关闭后立刻失效：session.isRunning 是异步翻的，closeIdle → open 会**在这段间隙复用刚关的
     * PTY，表现为新会话拿到同一个 sh 进程和上一个会话的旧屏幕（实测 echo $$ 相同）。 */
    @Volatile private var closed = false
    override val isAlive: Boolean get() = !closed && session.isRunning
    override val stream: Flow<TerminalTextChunk> get() = chunks.asSharedFlow()

    @Volatile private var sink: Channel<String>? = null

    /** 首屏（prompt）到达即完成：工具在 shell 就绪前写命令会被吞。 */
    internal val readySignal = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** 就绪前的写入缓存：TerminalSession.write 在 mShellPid<=0 时直接丢弃，必须自己兜着。 */
    private val pendingWrites = StringBuilder()
    @Volatile private var lastText: String = ""

    @Volatile private var deltaLogs = 0

    fun pushDelta(full: String) {
        if (!readySignal.isCompleted && full.isNotBlank()) {
            readySignal.complete(Unit)
            val queued = synchronized(pendingWrites) {
                if (pendingWrites.isEmpty()) null else pendingWrites.toString().also { pendingWrites.clear() }
            }
            if (queued != null) {
                session.write(queued)
                Logger.i("TermuxPty", "flush pending writes sid=$sid chars=${queued.length}")
            }
        }
        if (deltaLogs < 5) {
            deltaLogs++
            Logger.i("TermuxPty", "pushDelta sid=$sid fullLen=${full.length} sink=${sink != null}")
        }
        val prev = lastText
        // 快照 ≠ 增量：屏幕滚出顶行后 full 不再是 prev 的前缀，旧逻辑会把整屏再吐一次。
        // 这里按「prev 的后缀 == full 的前缀」求最大重叠，只吐真正新增的部分。
        val delta = when {
            full == prev -> ""
            full.startsWith(prev) -> full.substring(prev.length)
            else -> {
                val maxOverlap = minOf(prev.length, full.length, 2_000)
                var overlap = 0
                for (k in maxOverlap downTo 1) {
                    if (prev.regionMatches(prev.length - k, full, 0, k)) {
                        overlap = k
                        break
                    }
                }
                full.substring(overlap)
            }
        }
        lastText = full
        if (delta.isEmpty()) return
        chunks.tryEmit(TerminalTextChunk(com.hoerire.tools.libterm.OutputStream.STDOUT, delta, System.currentTimeMillis()))
        sink?.trySend(delta)
    }

    override suspend fun write(text: String) {
        if (readySignal.isCompleted) {
            session.write(text)
            return
        }
        // 就绪前写进缓存，首屏到达时统一补写（见 pushDelta）
        synchronized(pendingWrites) { pendingWrites.append(text) }
    }
    override suspend fun close() {
        closed = true
        runCatching { session.finishIfRunning() }
        // 必须从 TermuxPty.ports 摘掉，否则 open() 仍可能按 activePort().isAlive 复用死会话
        TermuxPty.closeSession(sid)
    }

    override suspend fun exec(command: String, timeoutMillis: Long): TermResult<CommandResult> {
        if (!isAlive) return TermResult.Failure(TerminalFailure.AlreadyClosed(TerminalIdentity.User, "pty dead"))
        val marker = "__ZAFIRO_DONE_" + System.nanoTime() + "__"
        val ch = Channel<String>(Channel.UNLIMITED)
        sink = ch
        val collected = StringBuilder()
        // PTY 刚 create 时 shell 还没起来，立刻 write 会被吞（表现为工具超时无输出）。
        // 先等首屏有内容（最多 2s），再写命令。
        withTimeoutOrNull(2_000L) {
            while (session.emulator?.screen?.transcriptText.isNullOrEmpty()) {
                kotlinx.coroutines.delay(50)
            }
        }
        session.write("\n$command; echo $marker:\$?\n")
        Logger.i("TermuxPty", "exec sid=$sid running=${session.isRunning} cmd=${command.take(40)}")
        var exit: Int? = null
        val finished = withTimeoutOrNull(timeoutMillis) {
            for (piece in ch) {
                collected.append(piece)
                val at = collected.indexOf(marker)
                if (at >= 0) {
                    exit = collected.substring(at + marker.length).removePrefix(":").trim().takeWhile { it.isDigit() }.toIntOrNull()
                    break
                }
            }
            true
        }
        sink = null
        val text = collected.toString().substringBefore(marker)
        return TermResult.Success(
            CommandResult(
                command = command,
                stdout = TerminalBytes.of(text.toByteArray()),
                stderr = TerminalBytes.EMPTY,
                exitCode = exit,
                timedOut = finished == null,
            ),
        )
    }

    companion object {
        fun create(sid: String, context: Context, cmd: String, cwd: String, args: Array<String>, env: Array<String>, identity: TerminalIdentity): TermuxPtyPort {
            lateinit var port: TermuxPtyPort
            val chunks = MutableSharedFlow<TerminalTextChunk>(extraBufferCapacity = 256)
            val client = object : TerminalSessionClient {
                override fun onTextChanged(session: TerminalSession) {
                    val full = session.emulator?.screen?.transcriptText ?: return
                    port.pushDelta(full)
                }
                override fun onTitleChanged(session: TerminalSession) {}
                override fun onSessionFinished(session: TerminalSession) { Log.i("TermuxPty", "finished $sid") }
                override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
                }
                override fun onPasteTextFromClipboard(session: TerminalSession?) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
                    session?.write(t)
                }
                override fun onBell(session: TerminalSession) {}
                override fun onColorsChanged(session: TerminalSession) {}
                override fun onTerminalCursorStateChange(state: Boolean) {}
                override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                override fun getTerminalCursorStyle(): Int? = 0
                override fun logError(tag: String, message: String) { Log.e(tag, message) }
                override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
                override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
                override fun logDebug(tag: String, message: String) {}
                override fun logVerbose(tag: String, message: String) {}
                override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
                override fun logStackTrace(tag: String, e: Exception) {}
            }
            val s = TerminalSession(cmd, cwd, args, env, 600, client)
            // 关键：TerminalSession 构造只存字段，initializeEmulator 才真正 forkpty 起 shell。
            // 之前没调，emulator 恒为 null、PTY 不存在 → 终端永远空白、工具全靠超时返回。
            // 列宽 200（原 80）：80 列会把长命令回显折断，回执里出现 `<` 和残缺行首
            // （HANDOFF-zterm3 §8.1）。这里的列数就是 PTY winsize，也决定 COLUMNS。
            s.initializeEmulator(200, 24, 8, 16)
            port = TermuxPtyPort(sid, s, chunks, identity)
            return port
        }
    }
}
