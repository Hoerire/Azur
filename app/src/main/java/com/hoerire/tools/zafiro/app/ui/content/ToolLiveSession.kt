package com.hoerire.tools.zafiro.app.ui.content

import com.hoerire.tools.zafiro.chat.agentic.shell.SessionRegistry
import com.hoerire.tools.zafiro.chat.agentic.shell.TerminalInteractiveReadOutcome
import com.hoerire.tools.zafiro.chat.agentic.shell.TerminalReadMode
import com.hoerire.tools.zafiro.util.AppContext
import com.hoerire.tools.zafiro.chat.agentic.shell.TerminalOpenOutcome
import com.hoerire.tools.zafiro.chat.agentic.shell.TerminalSessionPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

/**
 * 运行中工具的实时输出与行内停止。
 *
 * 全部走终端会话池的公开 API：
 * - 实时输出用 [TerminalReadMode.SNAPSHOT]，纯只读、不推进工具自己的 DELTA 读取游标；
 * - 停止是往该会话写 Ctrl-C，让命令自己结束，工具照常返回回执。
 *
 * 因此这一层完全不介入工具执行链路（不改 executor、不改 awaitSentinel），
 * 任何 UI 侧异常最多丢显示，不会卡住工具。
 */
internal object ToolLiveSession {

    private const val TAG = "ToolLive"
    private const val STATUS_RUNNING = "running"
    private const val MAX_BYTES = 64 * 1024
    private const val TAIL_CHARS = 2000

    /** 空闲终端的执行身份：应用默认（user）。 */
    private const val IDLE_IDENTITY = "user"

    /** 终端页只跟这些会话（本地 shell / ssh）；别的工具不占终端页。 */
    private val TERMINAL_SCOPES = setOf(
        SessionRegistry.SCOPE_LOCAL,
        SessionRegistry.SCOPE_SSH,
    )

    /** 停止链路自带作用域：块组合销毁也不会把送 Ctrl-C 的协程取消掉。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 当前正在跑命令的会话：先按命令文本对上，对不上就取唯一的运行中会话。 */
    fun runningSessionId(commandHint: String?): String? {
        val hint = commandHint?.trim().orEmpty()
        val running = SessionRegistry.all().mapNotNull { meta ->
            val state = SessionRegistry.getCommandState(meta.sessionId) ?: return@mapNotNull null
            if (state.status != STATUS_RUNNING) return@mapNotNull null
            meta.sessionId to state.command.trim()
        }
        if (running.isEmpty()) return null
        if (hint.isNotEmpty()) {
            running.firstOrNull { (_, command) -> command == hint }?.let { return it.first }
            running.firstOrNull { (_, command) ->
                command.isNotEmpty() && (hint.startsWith(command) || hint.contains(command))
            }?.let { return it.first }
        }
        return running.singleOrNull()?.first
    }

    /** 运行中命令的输出尾部；没有在跑的命令时返回空串。 */
    suspend fun liveTail(commandHint: String?): String {
        val sessionId = runningSessionId(commandHint) ?: return ""
        val outcome = TerminalSessionPool.readInteractive(
            session = sessionId,
            mode = TerminalReadMode.SNAPSHOT,
            maxBytes = MAX_BYTES,
        )
        val text = (outcome as? TerminalInteractiveReadOutcome.Success)?.text ?: return ""
        return tailOf(text)
    }

    /** 一次性快照：块晚进入组合时用它把前几秒已经输出的内容补种进 VT 屏。 */
    suspend fun snapshotText(commandHint: String?): String {
        val sessionId = runningSessionId(commandHint) ?: return ""
        val outcome = runCatching {
            TerminalSessionPool.readInteractive(
                session = sessionId,
                mode = TerminalReadMode.SNAPSHOT,
                maxBytes = MAX_BYTES,
            )
        }.getOrNull() ?: return ""
        return (outcome as? TerminalInteractiveReadOutcome.Success)?.text.orEmpty()
    }

    /** UI 侧只读直连：命中运行中的会话就返回其文本流（首帧 = 已有输出尾巴），否则 null。 */
    fun liveStream(commandHint: String?): Flow<String>? {
        val sessionId = runningSessionId(commandHint) ?: return null
        return runCatching { TerminalSessionPool.liveOutput(sessionId) }.getOrNull()
    }

    /** UI 侧只读直连（原始字节）：先到先得的 PTY 原始流，喂给块内 VT 渲染器。 */
    fun liveRawStream(commandHint: String?): Flow<ByteArray>? {
        val sessionId = runningSessionId(commandHint) ?: return null
        return runCatching { TerminalSessionPool.liveRawOutput(sessionId) }.getOrNull()
    }

    /**
     * 解析「正在跑的会话 + 原始字节流」，把 sessionId 一并带出来：
     * 退订看门狗要靠 sessionId 判断命令是否还在跑（拿命令原文再匹配一次会误伤同会话的下一条命令）。
     */
    fun liveRawBinding(commandHint: String?): LiveRawBinding? {
        val sessionId = runningSessionId(commandHint) ?: return null
        val flow = runCatching { TerminalSessionPool.liveRawOutput(sessionId) }.getOrNull() ?: return null
        return LiveRawBinding(sessionId, flow)
    }

    /** 原始字节快照：块晚进组合时用它补种，颜色 / 光标序列都在（文本快照是剥过的，无色）。 */
    suspend fun snapshotRaw(commandHint: String?): ByteArray? {
        val sessionId = runningSessionId(commandHint) ?: return null
        return runCatching { TerminalSessionPool.rawSnapshot(sessionId) }.getOrNull()
    }

    /**
     * 全屏终端页用：当前在跑的那条命令（sessionId to 命令原文）。
     * 这一页不依赖聊天页组合，所以得自己找「现在谁在跑」，不能等 preload。
     */
    fun runningCommand(): Pair<String, String>? {
        return runCatching {
            SessionRegistry.all().firstNotNullOfOrNull { meta ->
                val state = SessionRegistry.getCommandState(meta.sessionId)
                if (state == null || state.status != STATUS_RUNNING) {
                    null
                } else {
                    meta.sessionId to state.command.trim()
                }
            }
        }.getOrNull()
    }

    // ── 全屏「终端」页用（只认终端类会话：本地 shell / ssh）────────────────────

    /** 终端页的候选会话：会话 id + 命令原文 + 开始时刻。 */
    internal data class TerminalSession(
        val sessionId: String,
        val command: String,
        val startTimeMs: Long,
    )

    /**
     * 当前在跑的终端会话，按开始时间升序。
     *
     * 只认 [TERMINAL_SCOPES]：跑在独立进程里的工具不在会话表登记，
     * 所以终端页天然不会被它们占住（这里再按 scope 兜一道）。
     */
    fun terminalSessions(): List<TerminalSession> = runCatching {
        SessionRegistry.all().mapNotNull { meta ->
            if (meta.scope !in TERMINAL_SCOPES) return@mapNotNull null
            val state = SessionRegistry.getCommandState(meta.sessionId) ?: return@mapNotNull null
            if (state.status != STATUS_RUNNING) return@mapNotNull null
            TerminalSession(
                sessionId = meta.sessionId,
                command = state.command.trim(),
                startTimeMs = state.startTimeMs,
            )
        }.sortedBy { it.startTimeMs }
    }.getOrDefault(emptyList())

    /** 按会话 id 直连原始字节流（不要求命令仍在跑：键盘输入的回显、后续提示符都要收）。 */
    fun rawStreamOf(sessionId: String): Flow<ByteArray>? =
        runCatching { TerminalSessionPool.liveRawOutput(sessionId) }.getOrNull()

    /** 按会话 id 取原始字节快照，页面后开也能看到前几秒的输出与颜色。 */
    fun rawSnapshotOf(sessionId: String): ByteArray? =
        runCatching { TerminalSessionPool.rawSnapshot(sessionId) }.getOrNull()

    fun isSessionAlive(sessionId: String): Boolean =
        runCatching { TerminalSessionPool.isSessionAlive(sessionId) }.getOrDefault(false)

    // ── 空闲终端：没有命令在跑时，终端页自己开一条 shell，供手动敲命令 ────────────

    @Volatile
    private var idleSessionId: String? = null

    /** 页面自己开的空闲 shell：还活着就复用，死了或没开过就新开一条。 */
    suspend fun ensureIdleSession(): String? {
        idleSessionId?.let { existing -> if (isSessionAlive(existing)) return existing }
        // 起在应用私有目录：进程默认 cwd 往往是 `/` 之类不可读的地方（实测 `ls .` 都 Permission denied）
        val cwd = runCatching { AppContext.await().filesDir.absolutePath }.getOrNull()
        val opened = runCatching {
            when (
                val outcome = TerminalSessionPool.open(
                    identity = IDLE_IDENTITY,
                    cwd = cwd,
                    collectOutput = true,
                )
            ) {
                is TerminalOpenOutcome.Success -> outcome.session
                else -> null
            }
        }.getOrNull()
        if (opened != null) {
            idleSessionId = opened
            android.util.Log.i(TAG, "idle session opened=$opened")
        }
        return opened
    }

    /** 现成的空闲会话（没有就算了，不主动开）。 */
    fun idleSession(): String? = idleSessionId?.takeIf { isSessionAlive(it) }

    /**
     * 终端页标签上的「关闭」：先送 Ctrl-C 让命令自己收尾，再关会话、从登记表摘掉。
     *
     * 摘登记表是关键：只关池里的会话，`SessionRegistry` 里那条命令状态还会停在 running，
     * 标签会在下一轮轮询里「复活」。
     */
    fun closeSessionAsync(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        scope.launch {
            runCatching {
                TerminalSessionPool.writeInteractive(sessionId, "\u0003")
                delay(250)
                TerminalSessionPool.close(sessionId)
                SessionRegistry.unregister(sessionId)
            }
            android.util.Log.i(TAG, "close session=$sessionId")
        }
    }

    /**
     * 终端页键盘输入：整段写进该会话的 PTY（[TerminalSessionPool.writeInteractive]）。
     *
     * 只写不读：既不推进工具自己的 DELTA 读游标，也不取消任何协程；会话已关就静默丢弃。
     */
    fun writeInputAsync(sessionId: String?, text: String) {
        if (sessionId.isNullOrBlank() || text.isEmpty()) return
        scope.launch {
            val outcome = runCatching {
                TerminalSessionPool.writeInteractive(sessionId, text)
            }.getOrNull()
            android.util.Log.i(
                TAG,
                "write session=$sessionId chars=${text.length} outcome=" +
                    (outcome?.let { it::class.java.simpleName } ?: "null"),
            )
        }
    }

    /** 这条会话上的命令是否仍在 RUNNING（会话维度，不走命令原文匹配）。 */
    fun isCommandRunning(sessionId: String): Boolean {
        return runCatching {
            SessionRegistry.getCommandState(sessionId)?.status == STATUS_RUNNING
        }.getOrDefault(false)
    }

    /** [interrupt] 的即发即忘版：不挂在组合的 rememberCoroutineScope 上，块销毁也照常送 Ctrl-C。 */
    fun interruptAsync(commandHint: String?) {
        scope.launch { runCatching { interrupt(commandHint) } }
    }

    /**
     * 行内停止：先给会话送 Ctrl-C；PTY 若屏蔽了 SIGINT（raw 模式），1.5 秒后关会话兜底。
     * 两条路都不会取消任何协程，工具自己会以「正常结果 / 会话结束」收尾，整轮继续。
     */
    suspend fun interrupt(commandHint: String?): Boolean {
        val sessionId = runningSessionId(commandHint)
        if (sessionId == null) {
            android.util.Log.w(TAG, "stop: no running session, hint=${commandHint?.take(40)}")
            return false
        }
        val write = TerminalSessionPool.writeInteractive(sessionId, "\u0003")
        android.util.Log.i(TAG, "stop: ctrl-c session=$sessionId -> ${write::class.java.simpleName}")
        delay(1500)
        if (runningSessionId(commandHint) == null) {
            android.util.Log.i(TAG, "stop: ctrl-c worked, session=$sessionId")
            return true
        }
        val closed = TerminalSessionPool.close(sessionId)
        android.util.Log.i(TAG, "stop: escalate close session=$sessionId -> ${closed::class.java.simpleName}")
        return true
    }

    internal data class LiveRawBinding(
        val sessionId: String,
        val flow: Flow<ByteArray>,
    )

    private fun tailOf(raw: String): String {
        if (raw.isEmpty()) return ""
        val cleaned = if ('\u0000' in raw) raw.replace("\u0000", "") else raw
        val trimmed = cleaned.trimEnd()
        return if (trimmed.length > TAIL_CHARS) trimmed.takeLast(TAIL_CHARS) else trimmed
    }
}
