package com.hoerire.tools.zafiro.app

import android.content.Context
import android.util.Log
import com.hoerire.tools.zafiro.settings.RuntimeEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 宿主工具集 + 宿主脚本安装器。
 *
 * 两件事，装完首启各跑一次（幂等，标记 = APK lastUpdateTime）：
 *  1) assets/hosttools → files/hosttools（零 root，app 域自己就能 exec 私有目录 ELF）
 *  2) 有 root 时：把 assets/buildkit 里的宿主脚本落到 /data/local/aitools，
 *     并建换机后不存在的 5 个固定目录（源码/工程/产物/日志/临时）—— 没有这一步，
 *     新手机上 build-src.sh / bootstrap-env.sh 根本不存在，构建无从谈起。
 *
 * 全程用 ProcessBuilder 直接调 /system/bin/sh 和 su，不引 libsu 依赖，冷启动不拉授权框。
 */
object HostToolsInstaller {

    private const val TAG = "HostTools"
    private const val MAIN_DEST = "/data/local/aitools"

    /** 主包用 /data/local/aitools；测试包（com.hoerire.tools.btX）用 /data/local/aitools-btX，互不污染。 */
    private fun destRoot(ctx: Context): String {
        val suffix = ctx.packageName.removePrefix("com.hoerire.tools")
        return if (suffix.isEmpty()) MAIN_DEST
        else MAIN_DEST + suffix.replace('.', '-')
    }

    private val INIT_SCRIPTS = listOf("sign.init.gradle", "no-lint.init.gradle")
    private val HOST_SCRIPTS = listOf("bootstrap-env.sh", "build-src.sh", "env-doctor.sh", "hosttools-install.sh", "run.sh")
    private val FIXED_DIRS = listOf("源码", "工程", "产物", "日志", "临时", "环境")

    private fun home(ctx: Context) = File(ctx.filesDir, "hosttools")
    private fun stampFile(ctx: Context) = File(ctx.filesDir, "hosttools.stamp")

    fun isInstalled(ctx: Context): Boolean = runCatching {
        val m = stampFile(ctx)
        m.isFile && (stamp(ctx) == "0" || m.readText().trim() == stamp(ctx)) &&
            File(home(ctx), "TOOLS.txt").isFile && File(home(ctx), "bin/netrun").isFile
    }.getOrDefault(false)

    /** 阻塞执行，调用方放 IO 线程。 */
    fun install(ctx: Context): Boolean {
        // 根域必须每次冷启动都对齐（早退也要设），否则 zafiro_env 会回落主根
        val dest = destRoot(ctx)
        runCatching { RuntimeEnvironment.aitoolsRoot = dest }
        if (isInstalled(ctx)) {
            Log.i(TAG, "already installed: ${home(ctx)}")
            publishStatus(statusLine(ctx))
            // 早退时也要补建：不是只看根域在不在，而是关键资产缺一个就补
            // （首次无 root 打开 -> stamp 已写 -> 不再走安装主体；rootfs 重建后 shim 也会丢）
            val needDeploy = !File(dest).isDirectory ||
                !File(dest, "环境/toolchain/aapt2").isFile ||
                !File(dest, "环境/buildkit/sign.init.gradle").isFile ||
                !File(dest, "环境/rootfs/usr/local/bin/aapt2").isFile
            if (needDeploy) {
                runCatching {
                    val st = File(ctx.cacheDir, "ht-deploy").apply { deleteRecursively(); mkdirs() }
                    deployHostScripts(ctx, st)
                    Log.i(TAG, "root deployed on early-return: $dest")
                }.onFailure { Log.w(TAG, "early deploy failed", it) }
            }
            return true
        }
        val stage = File(ctx.cacheDir, "ht-stage")
        val ht = home(ctx)
        // 历史遗留：目录可能是 root 铺过的（老版本 / 手工 su 铺开），app 域既删不掉也 chmod 不动
        // → 表现为 "chmod Operation not permitted / static=0 musl=0"。先用 su 清掉再重铺。
        if (ht.exists() && !ht.canWrite()) {
            Log.w(TAG, "hosttools 目录不可写（属主非 app），用 su 清理后重铺")
            runCatching {
                ProcessBuilder("su", "-c", "rm -rf '${ht.absolutePath}'")
                    .redirectErrorStream(true).start().waitFor()
            }.onFailure { Log.w(TAG, "su 清理失败（无 root 时正常）", it) }
        }
        runCatching {
            stage.deleteRecursively()
            copyDir(ctx, "hosttools", File(stage, "hosttools"))
            ctx.assets.open("buildkit/hosttools-install.sh").use { i ->
                File(stage, "ht-install.sh").outputStream().use { o -> i.copyTo(o, 64 * 1024) }
            }
        }.onFailure { Log.w(TAG, "stage failed", it) }
        val script = File(stage, "ht-install.sh")
        if (!script.isFile) return false

        // 1) 私有目录铺开（不需要 root）
        val ok = runCatching {
            val p = ProcessBuilder("/system/bin/sh", script.absolutePath,
                stage.absolutePath, ht.absolutePath)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val rc = p.waitFor()
            Log.i(TAG, "local install rc=$rc ${out.lines().takeLast(4)}")
            rc == 0
        }.getOrElse { Log.w(TAG, "local install failed", it); false }

        // 2) 宿主脚本 + 固定目录（需要 root；失败不影响私有工具集）
        deployHostScripts(ctx, stage)

        // 3) 探测实际执行身份能否跑起来（App 进程 exec 私有目录会被 SELinux 拒，真正的调用方是 su）
        val diag = if (ok) probeExec(ht) else "skip: install failed"
        writeDiag(ctx, diag)
        publishStatus(diag)

        // 铺开成功即写 stamp：exec 受限属设备策略，与安装无关，不能因此每次启动重铺 113MB
        if (ok) stampFile(ctx).writeText(stamp(ctx))
        if (!diag.startsWith("ok")) Log.w(TAG, "hosttools 执行受限: $diag")
        Log.i(TAG, "install result=$ok diag=$diag path=$ht")
        return ok && isInstalled(ctx)
    }

    private fun diagFile(ctx: Context) = File(ctx.filesDir, "hosttools.diag")

    private fun writeDiag(ctx: Context, text: String) {
        runCatching { diagFile(ctx).writeText(text.take(400)) }
    }

    /** 供 PromptComposer 使用：读缓存诊断，缺失时补测一次。 */
    fun statusLine(ctx: Context): String {
        val cached = runCatching { diagFile(ctx).readText().trim() }.getOrNull()
        if (!cached.isNullOrBlank()) return cached
        val probed = probeExec(home(ctx))
        writeDiag(ctx, probed)
        return probed
    }

    private fun publishStatus(text: String) {
        runCatching { RuntimeEnvironment.hostToolsStatus = text }
    }

    /**
     * 探测 hosttools 是否真能执行：先 su（AI 的 shell 工具身份），再 App 进程。
     * App 进程 exec files/ 下 ELF 在 Android 10+ 会被 SELinux 拒（EACCES），这是常态，不算故障。
     */
    private fun probeExec(ht: File): String {
        val bin = File(ht, "bin/python3")
        if (!bin.isFile) return "missing: bin/python3"
        val viaSu = runProbe(arrayOf("su", "-c", "'${bin.absolutePath}' -V"))
        if (viaSu.startsWith("ok")) return "$viaSu (su)"
        val viaApp = runProbe(arrayOf(bin.absolutePath, "-V"))
        if (viaApp.startsWith("ok")) return "$viaApp (app)"
        return "exec_denied: su=$viaSu app=$viaApp"
    }

    private fun runProbe(cmd: Array<String>): String = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim().take(120)
        val done = p.waitFor(3, TimeUnit.SECONDS)
        when {
            !done -> { p.destroyForcibly(); "timeout" }
            p.exitValue() == 0 -> "ok: ${out.lineSequence().firstOrNull() ?: "ok"}"
            else -> "fail(${p.exitValue()}): $out"
        }
    }.getOrElse { "denied: ${it.message?.take(60)}" }

    /** 把脚本落到 /data/local/aitools 并建固定目录；无 root 时静默失败。 */
    private fun deployHostScripts(ctx: Context, stage: File) {
        val dest = destRoot(ctx)
        runCatching { RuntimeEnvironment.aitoolsRoot = dest }
        runCatching {
            val cmd = StringBuilder("mkdir -p $dest; ")
            FIXED_DIRS.forEach { cmd.append("mkdir -p '$dest/$it'; ") }
            // 测试根域共享主环境的只读工具链（2.8G），工程/产物/日志各自独立
            if (dest != MAIN_DEST) {
                cmd.append("[ -e '$dest/环境' ] || ln -s '$MAIN_DEST/环境' '$dest/环境'; ")
            }
            HOST_SCRIPTS.forEach { name ->
                ctx.assets.open("buildkit/$name").use { i ->
                    File(stage, name).outputStream().use { o -> i.copyTo(o, 64 * 1024) }
                }
                cmd.append("cp -f '${File(stage, name).absolutePath}' '$dest/$name'; ")
                cmd.append("chmod 755 '$dest/$name'; ")
            }
            // DEPLOY_TOOLCHAIN: aapt2(bionic aarch64) + wrapper + musl libz，容器内 aapt2 必需
            runCatching {
                val tc = File(stage, "toolchain").apply { mkdirs() }
                listOf("aapt2", "aapt2.wrapper.sh", "lib/libz.so.1").forEach { rel ->
                    val out = File(tc, rel)
                    out.parentFile?.mkdirs()
                    ctx.assets.open("toolchain/$rel").use { i ->
                        out.outputStream().use { o -> i.copyTo(o, 64 * 1024) }
                    }
                    out.setExecutable(true, false)
                }
                cmd.append("mkdir -p '$dest/环境/toolchain/lib'; ")
                cmd.append("cp -f '${File(tc, "aapt2").absolutePath}' '$dest/环境/toolchain/aapt2'; ")
                cmd.append("cp -f '${File(tc, "aapt2.wrapper.sh").absolutePath}' '$dest/环境/toolchain/aapt2.wrapper.sh'; ")
                cmd.append("cp -f '${File(tc, "lib/libz.so.1").absolutePath}' '$dest/环境/toolchain/lib/libz.so.1'; ")
                cmd.append("chmod 755 '$dest/环境/toolchain/aapt2' '$dest/环境/toolchain/aapt2.wrapper.sh'; ")
                // 容器内 AGP 用 /usr/local/bin/aapt2（rootfs 重建后会丢，这里每次补齐）
                cmd.append("mkdir -p '$dest/环境/rootfs/usr/local/bin'; ")
                cmd.append("printf '#!/bin/sh\\nLD_LIBRARY_PATH=%s/环境/toolchain/lib:/system/lib64:/vendor/lib64 exec %s/环境/toolchain/aapt2 \"\$@\"\\n' '$dest' '$dest' > '$dest/环境/rootfs/usr/local/bin/aapt2'; ")
                cmd.append("chmod 755 '$dest/环境/rootfs/usr/local/bin/aapt2'; ")
            }.onFailure { Log.w(TAG, "deploy toolchain failed", it) }
            INIT_SCRIPTS.forEach { name ->
                ctx.assets.open("buildkit/$name").use { i ->
                    File(stage, name).outputStream().use { o -> i.copyTo(o, 64 * 1024) }
                }
                cmd.append("mkdir -p '$dest/环境/buildkit'; ")
                cmd.append("cp -f '${File(stage, name).absolutePath}' '$dest/环境/buildkit/$name'; ")
            }
            cmd.append("mkdir -p '$dest/环境' 2>/dev/null; cp -f '$dest/run.sh' '$dest/环境/run.sh' 2>/dev/null; chmod 755 '$dest/环境/run.sh' 2>/dev/null; ")
            cmd.append("chmod 755 '$dest' 2>/dev/null; echo HOSTSCRIPTS_OK")
            val p = ProcessBuilder("su", "-c", cmd.toString()).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val rc = p.waitFor()
            Log.i(TAG, "host scripts rc=$rc ${out.trim().takeLast(120)}")
        }.onFailure { Log.w(TAG, "deployHostScripts failed（无 root 时正常）", it) }
    }

    private fun copyDir(ctx: Context, assetPath: String, out: File) {
        val children = ctx.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            out.parentFile?.mkdirs()
            ctx.assets.open(assetPath).use { i -> out.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
            out.setExecutable(true, false)
        } else {
            out.mkdirs()
            children.forEach { copyDir(ctx, "$assetPath/$it", File(out, it.removePrefix("x__"))) }
        }
    }

    /**
     * hosttools 内容版本：assets/hosttools/TOOLS.txt 的 SHA-1。
     * 绑内容而不是 APK lastUpdateTime —— 升级但 hosttools 没变时不再重铺 113MB
     * （重铺会删旧目录 + 拷 113MB + 拆 305 applet，首启必被系统判卡死）。
     */
    private fun stamp(ctx: Context): String = runCatching {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        ctx.assets.open("hosttools/VERSION").use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("0")
}
