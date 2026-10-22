package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hoerire.tools.zafiro.app.ui.content.vt.VtScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 从工具参数 JSON 取 shell 命令原文，用于匹配正在运行的终端会话。 */
internal fun commandOf(inputText: String?): String {
    val raw = inputText?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    return runCatching {
        Json.parseToJsonElement(raw).jsonObject["command"]?.jsonPrimitive?.contentOrNull
    }.getOrNull().orEmpty()
}

/**
 * 运行中的命令型工具：worker 把 stdout/stderr（含 subprocess 子进程）追加写进
 * `cacheDir/py_output/<uuid>.log`，工具结束即被 Java 侧消费删除。
 *
 * 读取策略：运行期间每 250ms 取「最新的 .log」并读末 4KB —— 文件很小、只在该块
 * 组合且工具运行中才跑，开销可忽略；不用事件驱动重读，避免事件风暴把读协程
 * 反复取消（历史上就是因此一直空着）。FileObserver 只用来打点，便于排障。
 */
@Composable
internal fun livePyOutput(running: Boolean): String {
    var text by remember { mutableStateOf("") }
    val cacheDir = LocalContext.current.cacheDir
    LaunchedEffect(running, cacheDir) {
        if (!running) return@LaunchedEffect
        val dir = java.io.File(cacheDir, "py_output")
        while (true) {
            val newest = runCatching {
                dir.listFiles()?.filter { it.isFile && it.name.endsWith(".log") }
                    ?.maxByOrNull { it.lastModified() }
            }.getOrNull()
            if (newest != null) {
                text = readTail(newest, 4_000, text)
            }
            delay(250)
        }
    }
    return text
}

/** 读文件末 [maxChars] 字符；读不到或为空时保留上一次文本。 */
private suspend fun readTail(file: java.io.File, maxChars: Int, fallback: String): String =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val length = file.length()
            if (length <= 0L) return@runCatching fallback
            java.io.RandomAccessFile(file, "r").use { raf ->
                val keep = minOf(length, maxChars.toLong()).toInt()
                raf.seek(length - keep)
                val buf = ByteArray(keep)
                raf.readFully(buf)
                String(buf, Charsets.UTF_8).ifBlank { fallback }
            }
        }.getOrDefault(fallback)
    }

/**
 * 运行中的命令工具：直连 PTY 会话流（[ToolLiveSession.liveStream]），首帧即当前输出尾巴，
 * 之后逐块追加，延迟 ≤ 一帧。会话注册有先后，2 秒内没命中就退回 400ms 快照轮询兜底。
 * 全程只读，不消费工具自己的 DELTA 游标。
 */
@Composable
internal fun liveCommandOutput(running: Boolean, hint: String): String {
    var text by remember(hint) { mutableStateOf("") }
    LaunchedEffect(running, hint) {
        if (!running) return@LaunchedEffect
        var live: Flow<String>? = null
        var waited = 0L
        while (live == null && waited < 2_000L) {
            live = runCatching { ToolLiveSession.liveStream(hint) }.getOrNull()
            if (live == null) {
                delay(100)
                waited += 100
            }
        }
        val stream = live
        if (stream != null) {
            val maxChars = 4_000
            val buffer = StringBuilder(maxChars * 2)
            stream.collect { chunk ->
                buffer.append(chunk)
                if (buffer.length > maxChars) buffer.delete(0, buffer.length - maxChars)
                text = buffer.toString()
            }
            return@LaunchedEffect
        }
        // 兜底：拿不到会话（远端/异进程实现）仍按 400ms 快照刷新
        while (true) {
            text = runCatching { ToolLiveSession.liveTail(hint) }.getOrDefault("")
            delay(400)
        }
    }
    return text
}


/**
 * 块内真终端的屏幕注册表：屏幕实例与采集协程都挂在应用级 scope 上，
 * 不随块进出组合而丢（聊天列表滚出屏幕时组合会被销毁，挂在 LaunchedEffect 上会断流）。
 */
internal object ToolTerminals {

    private const val TAG = "ToolVT"

    /** 命令结束后再等这么久，把最后几块输出收进来再退订。 */
    private const val DRAIN_GRACE_MS = 800L

    /** 命令状态轮询间隔。 */
    private const val WATCHDOG_INTERVAL_MS = 400L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screens = HashMap<String, VtScreen>()
    private val jobs = HashMap<String, Job>()
    private val watchdogs = HashMap<String, Job>()

    fun screenOf(key: String): VtScreen = screens.getOrPut(key) { VtScreen() }

    fun attach(key: String, hint: String) {
        // 全屏页跟着最新命令走：先记下来，再决定要不要真的起协程
        lastKey = key
        lastHint = hint
        if (jobs[key]?.isActive == true) return
        val screen = screenOf(key)
        jobs[key] = scope.launch {
            android.util.Log.i(TAG, "attach key=$key hint=${hint.take(30)} screen=${System.identityHashCode(screen)}")
            var waited = 0L
            var binding: ToolLiveSession.LiveRawBinding? = null
            while (binding == null && waited < 4_000L) {
                binding = runCatching { ToolLiveSession.liveRawBinding(hint) }.getOrNull()
                if (binding == null) {
                    delay(100)
                    waited += 100
                }
            }
            val live = binding
            if (live == null) {
                android.util.Log.i(TAG, "no stream key=$key")
                return@launch
            }
            // 块是进入组合之后才订阅的（聊天列表懒加载），这之前的输出收不到，先补种一把。
            // 优先原始字节（颜色 / 光标序列都在），拿不到才退回纯文本快照（那几行没有颜色）。
            if (screen.lineCount == 0) {
                val raw = runCatching { ToolLiveSession.snapshotRaw(hint) }.getOrNull()
                if (raw != null && raw.isNotEmpty()) {
                    screen.feed(raw)
                    android.util.Log.i(TAG, "seed key=$key raw=${raw.size}B lines=${screen.lineCount}")
                } else {
                    val seed = runCatching { ToolLiveSession.snapshotText(hint) }.getOrNull().orEmpty()
                    if (seed.isNotEmpty()) {
                        screen.feed(seed.toByteArray())
                        android.util.Log.i(TAG, "seed key=$key text chars=${seed.length} lines=${screen.lineCount} plain")
                    }
                }
            }
            lastSessionId = live.sessionId
            // 会话归属：同一会话被新命令接管时，让旧块停手（后台命令不会因此断流）。
            bindSession(key, live.sessionId)
            startWatchdog(key, live.sessionId)
            var chunks = 0
            try {
                live.flow.buffer(Channel.UNLIMITED).collect { bytes ->
                    chunks++
                    screen.feed(bytes)
                    // 只留低频诊断：每 100 块打一次屏内容，排查渲染问题时够用，不刷日志
                    if (chunks % 100 == 0) {
                        android.util.Log.i(TAG, "chunks=$chunks lines=${screen.lineCount} dump=" + screen.debugDump(4))
                    }
                }
            } catch (cancelled: CancellationException) {
                android.util.Log.i(TAG, "cancelled chunks=$chunks")
                throw cancelled
            } finally {
                stopWatchdog(key)
            }
            android.util.Log.i(TAG, "end chunks=$chunks lines=${screen.lineCount}")
            android.util.Log.i(TAG, "dump=" + screen.debugDump(6))
        }
    }

    /**
     * 全屏「终端」页专用：按**会话 id** 直连订阅，不要求「命令正在跑」。
     *
     * 和 [attach] 的差别：命令结束时 shell 还活着，这边继续收 —— 键盘输入的回显、
     * 后续提示符都看得到；只有会话真的没了（shell 退出 / ssh 断连）才退订。
     * 同一 key 已有同一会话的活跃订阅就直接返回，页面每 600ms 轮询也不会重复起协程。
     */
    /**
     * @param header true = 屏还空着时，把命令行本身（`$ cmd`）作为第一行写进去。
     *   全屏页要这个（命令直接出现在输出里，不另占一行 UI）；聊天页的块不要。
     */
    fun attachSession(
        key: String,
        sessionId: String,
        hint: String,
        header: Boolean = false,
    ) {
        lastKey = key
        lastHint = hint
        lastSessionId = sessionId
        if (jobs[key]?.isActive == true && sessionOf[key] == sessionId) return
        jobs.remove(key)?.cancel()
        stopWatchdog(key)
        val screen = screenOf(key)
        jobs[key] = scope.launch {
            val live = ToolLiveSession.rawStreamOf(sessionId)
            if (live == null) {
                android.util.Log.i(TAG, "page no-stream key=$key session=$sessionId")
                return@launch
            }
            val emptyBefore = screen.lineCount == 0
            if (emptyBefore) {
                val seed = ToolLiveSession.rawSnapshotOf(sessionId)
                if (seed != null && seed.isNotEmpty()) {
                    screen.feed(seed)
                    android.util.Log.i(TAG, "page seed key=$key raw=${seed.size}B lines=${screen.lineCount}")
                }
            }
            // 命令本身写进流里（第一行 `$ cmd`）：页面上不再单独占一行，命令也不会被省略号折叠
            if (header && emptyBefore && hint.isNotBlank()) {
                screen.feed(("\$ " + hint.replace('\n', ' ').trim() + "\n").toByteArray())
            }
            sessionOf[key] = sessionId
            startSessionWatchdog(key, sessionId)
            var chunks = 0
            try {
                live.buffer(Channel.UNLIMITED).collect { bytes ->
                    chunks++
                    screen.feed(bytes)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                stopWatchdog(key)
            }
            android.util.Log.i(TAG, "page end key=$key chunks=$chunks lines=${screen.lineCount}")
        }
    }

    /** 会话级看门狗：shell 退出 / ssh 断连（会话不再活着）才退订。 */
    private fun startSessionWatchdog(key: String, sessionId: String) {
        stopWatchdog(key)
        watchdogs[key] = scope.launch {
            while (isActive) {
                delay(1_000)
                if (ToolLiveSession.isSessionAlive(sessionId)) continue
                android.util.Log.i(TAG, "detach key=$key session=$sessionId reason=session-closed")
                jobs.remove(key)?.cancel()
                watchdogs.remove(key)
                return@launch
            }
        }
    }

    /** key → 它当前绑定的 PTY 会话；一个会话同时只该有一个块在收。 */
    private val sessionOf = HashMap<String, String>()

    // 全屏「终端」页要跟着最新的那条命令走，所以最近一次挂载的目标要能被组合层观察到。
    private var lastKey by mutableStateOf<String?>(null)
    private var lastHint by mutableStateOf("")
    private var lastSessionId by mutableStateOf("")

    /** 全屏终端页的显示目标：最近一次挂上采集的那块屏。 */
    internal data class TerminalTarget(
        val key: String,
        val hint: String,
        val sessionId: String,
        val screen: VtScreen,
    )

    /** 这个 PTY 会话当前归哪个 key 在收；没有就返回 null（聊天页还没挂过它）。 */
    fun keyOfSession(sessionId: String): String? =
        sessionOf.entries.firstOrNull { it.value == sessionId }?.key

    fun latestTarget(): TerminalTarget? {
        val key = lastKey ?: return null
        return TerminalTarget(
            key = key,
            hint = lastHint,
            sessionId = lastSessionId,
            screen = screenOf(key),
        )
    }

    /** 快路径：块看到状态不再是 RUNNING 就立刻退订，不等看门狗下一轮。 */
    fun detach(key: String, reason: String = "composable") {
        sessionOf.remove(key)
        val job = jobs.remove(key) ?: return
        if (job.isActive) android.util.Log.i(TAG, "detach key=$key reason=$reason")
        job.cancel()
        stopWatchdog(key)
    }

    /**
     * 会话归属：一个 PTY 会话同一时刻只归一个块。新命令复用了旧块还在收的会话时，
     * 先把旧块的采集退掉 —— 否则旧块会继续被「后面跑的命令」喂，这正是遗留②。
     *
     * 不能改用「工具调用结束」当信号：后台命令的调用在返回那一刻就 Done 了，命令本身还在跑。
     */
    private fun bindSession(key: String, sessionId: String) {
        val stale = sessionOf.entries
            .filter { it.key != key && it.value == sessionId }
            .map { it.key }
        stale.forEach { detach(it, "session-reused") }
        sessionOf[key] = sessionId
    }

    /**
     * 退订看门狗：命令状态从 RUNNING 变成别的（或状态条目消失）就取消采集。
     * [DRAIN_GRACE_MS] 的宽限是留给最后几块输出的排空时间。
     */
    private fun startWatchdog(key: String, sessionId: String) {
        stopWatchdog(key)
        watchdogs[key] = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (ToolLiveSession.isCommandRunning(sessionId)) continue
                delay(DRAIN_GRACE_MS)
                if (ToolLiveSession.isCommandRunning(sessionId)) continue
                android.util.Log.i(TAG, "detach key=$key session=$sessionId reason=command-finished")
                jobs.remove(key)?.cancel()
                watchdogs.remove(key)
                return@launch
            }
        }
    }

    private fun stopWatchdog(key: String) {
        watchdogs.remove(key)?.cancel()
    }
}

/**
 * 块内真终端：按 [key]（callId）取一块 [VtScreen]，运行中让 [ToolTerminals] 把**原始 PTY 字节**
 * 喂进去（ANSI 颜色 / 光标 / `\r` 覆盖全保留），跑完不清空 —— 结束后同一个块继续用这块屏渲染。
 * 没有内容时 [CodeToolBody] 自动回落到文本路径。
 */
@Composable
internal fun liveTerminal(running: Boolean, hint: String, key: String): VtScreen {
    val screen = remember(key) { ToolTerminals.screenOf(key) }
    // 预挂之后块才组合出来时，第一次组合的 running 还可能是 false（状态还没翻到 Running），
    // 那不是「跑完了」，不能拿它去退订；只有 true → false 的翻转才算结束。
    var wasRunning by remember(key) { mutableStateOf(false) }
    LaunchedEffect(running, hint, key) {
        if (running) {
            wasRunning = true
            ToolTerminals.attach(key, hint)
        } else if (wasRunning) {
            wasRunning = false
            ToolTerminals.detach(key)
        }
    }
    return screen
}