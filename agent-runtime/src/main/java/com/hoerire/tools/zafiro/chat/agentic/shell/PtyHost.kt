package com.hoerire.tools.zafiro.chat.agentic.shell

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import com.hoerire.tools.libterm.OpenResult
import com.hoerire.tools.libterm.TerminalBytes
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.runtime.CommandResult
import com.hoerire.tools.libterm.runtime.TermResult
import com.hoerire.tools.libterm.runtime.TerminalTextChunk
import com.hoerire.tools.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileDescriptor

/**
 * PTY 底座（libptyhost.so，60 行裸 syscall，无 libc/第三方依赖）：
 * openat("/dev/ptmx") + TIOCSPTLCK(unlock) + TIOCGPTN(取号) + TIOCSWINSZ(尺寸)。
 * android.system.Os 没有 ioctl，反射 libcore 又被 hiddenapi 挡住（targetSdk 34 实测），所以只有这一层是 native。
 */
internal object PtyNative {
    init { System.loadLibrary("ptyhost") }
    external fun nativeOpenPty(): Int
    external fun nativePtsIndex(fd: Int): Int
    external fun nativeSetWinSize(fd: Int, rows: Int, cols: Int): Int
}

/**
 * PTY 会话宿主（除锁 slave 的那次 ioctl 外全 Kotlin）。
 *
 * 建会话：ptmx+unlock -> ProcessBuilder 把 stdin/stdout/stderr 重定向到 /dev/pts/N
 *         -> setsid -c 取得 controlling terminal -> exec shell（Su 身份即 exec su）。
 * 读输出：读线程循环 Os.read(master)，剥 ANSI 后按增量推给 UI 与 exec 收集器。
 */
object PtyHost {
    private const val TAG = "PtyHost"
    private const val ROWS = 24

    /** 列宽 200：80 列会把长命令回显折断（HANDOFF-zterm3 8.1），也决定 COLUMNS。 */
    private const val COLS = 200
    private const val PTY_MARKER = "ZAFIRO_PTY=1"

    private val ports = LinkedHashMap<String, PtySession>()
    private var seq = 0

    init { sweepOrphans() }

    internal fun activePort(): PtySession? = synchronized(ports) { ports.values.lastOrNull() }

    internal fun closeSession(id: String) {
        synchronized(ports) { ports.remove(id) }
    }

    internal suspend fun open(
        identity: TerminalIdentity,
        cwd: String?,
        contextProvider: suspend () -> Context,
    ): OpenResult<TerminalSessionPort> {
        // 复用只认同 identity：否则 user 的空闲会话会被 root 工具拿去跑（跨权限串台）。
        synchronized(ports) { ports.values.lastOrNull { it.identity == identity && it.isAlive } }
            ?.let { return OpenResult.Success(it) }
        return runCatching { createSession(contextProvider(), identity, cwd) }.fold(
            onSuccess = { OpenResult.Success(it) },
            onFailure = { OpenResult.Failure(TerminalFailure.StartupFailed(identity, it.message, it)) },
        )
    }

    private fun createSession(context: Context, identity: TerminalIdentity, cwd: String?): PtySession =
        synchronized(ports) {
            val p = build(context, "s" + (++seq), identity, cwd)
            ports[p.id] = p
            p
        }

    private fun build(context: Context, sid: String, identity: TerminalIdentity, cwd: String?): PtySession {
        val home = File(context.filesDir, "pty-home").apply { mkdirs() }
        home.mkdirs()

        val shell = if (identity == TerminalIdentity.Su) "/system/bin/su" else "/system/bin/sh"
        val workDir = when {
            identity == TerminalIdentity.Su -> cwd ?: "/"
            !cwd.isNullOrBlank() -> cwd
            else -> home.absolutePath
        }
        val env = mapOf(
            "TERM" to "xterm-256color",
            "HOME" to home.absolutePath,
            "PWD" to workDir,
            "PATH" to "/system/bin:/system/xbin:/sbin",
            "LANG" to "en_US.UTF-8",
            // 孤儿兜底：App 被强杀时 finally 不跑，子进程挂到 init(1) 下，启动时按此标记认人。
            "ZAFIRO_PTY" to "1",
        )
        Logger.i(TAG, "create " + sid + " identity=" + identity + " shell=" + shell + " cwd=" + workDir)
        return PtySession.create(sid, identity, shell, workDir, env)
    }

    /** 清掉上一条命遗留的 PTY 子进程：只认 PPID==1 且 environ 带标记的，避免误杀别的活会话。 */
    private fun sweepOrphans() {
        val selfPid = android.os.Process.myPid()
        var killed = 0
        for (dir in File("/proc").listFiles() ?: return) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid == selfPid || !isOrphan(pid)) continue
            val environ = runCatching { File(dir, "environ").readBytes() }.getOrNull() ?: continue
            if (!String(environ, Charsets.UTF_8).contains(PTY_MARKER)) continue
            if (runCatching { android.os.Process.sendSignal(pid, 9) }.isSuccess) killed++
        }
        if (killed > 0) Logger.i(TAG, "sweep orphan ptys killed=" + killed)
    }

    /** /proc/<pid>/stat 的 ppid 是否为 1；comm 可能带空格括号，按最后一个 ')' 切。 */
    private fun isOrphan(pid: Int): Boolean = runCatching {
        val stat = File("/proc/" + pid + "/stat").readText()
        stat.substringAfterLast(')').trim().split(' ').getOrNull(1)?.toInt() == 1
    }.getOrDefault(false)

    /** 开一对 pty：master 走 adoptFd 交给 PFD 管生命周期，winsize 在 master 上设、slave 侧继承。 */
    private fun createPty(): Pair<ParcelFileDescriptor, String> {
        val fd = PtyNative.nativeOpenPty()
        if (fd < 0) throw IllegalStateException("ptmx open/unlockpt failed")
        val idx = runCatching { PtyNative.nativePtsIndex(fd) }.getOrDefault(-1)
        if (idx < 0) {
            runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
            throw IllegalStateException("TIOCGPTN failed fd=" + fd)
        }
        val pfd = ParcelFileDescriptor.adoptFd(fd)
        runCatching { PtyNative.nativeSetWinSize(fd, ROWS, COLS) }
        return pfd to ("/dev/pts/" + idx)
    }

    /** 子进程 stdin/stdout/stderr 全部指向 slave，再由 setsid -c 把 slave 设成 controlling tty。 */
    private fun spawn(slavePath: String, workDir: String, shell: String, env: Map<String, String>): Process {
        val pb = ProcessBuilder("/system/bin/setsid", "-c", "/system/bin/sh", "-c", "exec " + shell)
        pb.directory(File(workDir))
        pb.environment().putAll(env)
        val slave = File(slavePath)
        pb.redirectInput(ProcessBuilder.Redirect.from(slave))
        pb.redirectOutput(ProcessBuilder.Redirect.to(slave))
        pb.redirectError(ProcessBuilder.Redirect.to(slave))
        return pb.start()
    }

    internal fun newSession(sid: String, identity: TerminalIdentity, shell: String, workDir: String, env: Map<String, String>): PtySession {
        val (master, slavePath) = createPty()
        Logger.i(TAG, "pty " + sid + " master->" + slavePath)
        val proc = spawn(slavePath, workDir, shell, env)
        return PtySession(sid, identity, master, proc)
    }
}

/** 一条 PTY 会话：读线程 + ANSI 过滤 + marker 驱动的 exec。 */
internal class PtySession(
    private val sid: String,
    val identity: TerminalIdentity,
    private val master: ParcelFileDescriptor,
    private val proc: Process,
) : TerminalSessionPort {
    override val id: String get() = sid
    override val stream: Flow<TerminalTextChunk> get() = chunks.asSharedFlow()

    private val chunks = MutableSharedFlow<TerminalTextChunk>(extraBufferCapacity = 256)
    @Volatile private var closed = false
    @Volatile private var sink: Channel<String>? = null
    @Volatile private var deltaLogs = 0

    private val readySignal = CompletableDeferred<Unit>()
    private val pendingWrites = StringBuilder()
    private val filter = AnsiFilter()

    /** 原始字节尾巴（未剥 ANSI），供 readSession 补种。 */
    private val raw = ByteArray(16 * 1024)
    private var rawLen = 0
    private var rawPos = 0

    private val fd: FileDescriptor get() = master.fileDescriptor

    override val isAlive: Boolean get() = !closed && proc.isAlive

    override fun rawSnapshot(maxBytes: Int): ByteArray {
        val out = ByteArray(minOf(rawLen, maxBytes))
        var start = rawPos - out.size
        if (start < 0) start += raw.size
        for (i in out.indices) out[i] = raw[(start + i) % raw.size]
        return out
    }

    private fun start() {
        Thread({ readLoop() }, "pty-read-" + sid).apply { isDaemon = true; start() }
    }

    private fun readLoop() {
        val buf = ByteArray(8192)
        while (!closed) {
            val n = try {
                Os.read(fd, buf, 0, buf.size)
            } catch (e: Throwable) {
                if (!closed) Logger.i(TAG, "read end " + sid + " " + e.message)
                -1
            }
            if (n <= 0) break
            appendRaw(buf, n)
            val text = filter.feed(buf, n)
            if (text.isNotEmpty()) emit(text)
        }
        readySignal.complete(Unit)
        Logger.i(TAG, "reader exit " + sid + " alive=" + proc.isAlive)
    }

    private fun appendRaw(buf: ByteArray, n: Int) {
        for (i in 0 until n) {
            raw[rawPos] = buf[i]
            rawPos = (rawPos + 1) % raw.size
            if (rawLen < raw.size) rawLen++
        }
    }

    private fun emit(delta: String) {
        if (!readySignal.isCompleted) {
            readySignal.complete(Unit)
            val queued = synchronized(pendingWrites) {
                if (pendingWrites.isEmpty()) null else pendingWrites.toString().also { pendingWrites.clear() }
            }
            if (queued != null) send(queued)
        }
        if (deltaLogs < 5) {
            deltaLogs++
            Logger.i(TAG, "delta " + sid + " len=" + delta.length + " sink=" + (sink != null))
        }
        chunks.tryEmit(TerminalTextChunk(com.hoerire.tools.libterm.OutputStream.STDOUT, delta, System.currentTimeMillis()))
        sink?.trySend(delta)
    }

    private fun send(text: String) {
        val b = text.toByteArray(Charsets.UTF_8)
        var off = 0
        while (off < b.size) {
            val w = try {
                Os.write(fd, b, off, b.size - off)
            } catch (e: Throwable) {
                Logger.i(TAG, "write fail " + sid + " " + e.message)
                return
            }
            if (w <= 0) return
            off += w
        }
    }

    override suspend fun write(text: String) {
        if (readySignal.isCompleted) {
            send(text)
            return
        }
        synchronized(pendingWrites) { pendingWrites.append(text) }
    }

    override suspend fun exec(command: String, timeoutMillis: Long): TermResult<CommandResult> {
        if (!isAlive) return TermResult.Failure(TerminalFailure.AlreadyClosed(identity, "pty dead"))
        // shell 还没出首屏就写命令会被吞（表现为工具超时无输出），先等就绪最多 3s。
        withTimeoutOrNull(3_000L) { readySignal.await() }
        val marker = "__ZAFIRO_DONE_" + System.nanoTime() + "__"
        val ch = Channel<String>(Channel.UNLIMITED)
        sink = ch
        send("\n" + command + "; echo " + marker + ":$?\n")
        Logger.i(TAG, "exec " + sid + " alive=" + proc.isAlive + " cmd=" + command.take(40))
        var exit: Int? = null
        val collected = StringBuilder()
        val finished = withTimeoutOrNull(timeoutMillis) {
            for (piece in ch) {
                collected.append(piece)
                val at = collected.indexOf(marker)
                if (at >= 0) {
                    exit = collected.substring(at + marker.length).removePrefix(":").trim()
                        .takeWhile { it.isDigit() }.toIntOrNull()
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

    override suspend fun close() {
        closed = true
        runCatching { master.close() }
        runCatching { proc.destroy() }
        PtyHost.closeSession(sid)
    }

    companion object {
        private const val TAG = "PtyHost"

        fun create(sid: String, identity: TerminalIdentity, shell: String, workDir: String, env: Map<String, String>): PtySession =
            PtyHost.newSession(sid, identity, shell, workDir, env).also { it.start() }
    }
}

/**
 * 极简 ANSI 过滤器：丢掉 CSI/OSC/单字符转义、\r（回车重绘）、控制字符，保留 \n \t，\b 退格删字。
 * 目的是给工具和对话框一份干净文本，不做光标定位/屏幕模型。
 */
private class AnsiFilter {
    private val sb = StringBuilder()
    private var state = 0 // 0=正文 1=ESC 2=CSI 3=OSC 4=OSC-ESC

    fun feed(buf: ByteArray, n: Int): String {
        for (i in 0 until n) {
            val c = buf[i].toInt() and 0xff
            when (state) {
                0 -> when {
                    c == 0x1b -> state = 1
                    c == '\n'.code || c == '\t'.code -> sb.append(c.toChar())
                    c == '\b'.code -> if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                    c < 0x20 || c == 0x7f -> Unit
                    else -> sb.append(c.toChar())
                }
                1 -> when (c) {
                    '['.code -> state = 2
                    ']'.code -> state = 3
                    else -> state = 0
                }
                2 -> if (c in 0x40..0x7e) state = 0
                3 -> when (c) {
                    0x07 -> state = 0
                    0x1b -> state = 4
                    else -> Unit
                }
                4 -> state = if (c == '\\'.code) 0 else 3
            }
        }
        if (sb.isEmpty()) return ""
        val out = sb.toString()
        sb.setLength(0)
        return out
    }
}
