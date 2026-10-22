package com.hoerire.tools.zafiro.chat.agentic.buildin.impl

import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinTool
import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinToolRequest
import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * 本机构建环境入口工具（zafiro_env）。
 * 包一层 ${ROOT}/env/env.sh：App 构建、native 工具链检查、扩展包解压、容器内命令。
 * 构建走后台 + 日志，避免把工具调用卡在几分钟的 gradle 上。
 */
class ZafiroEnvBuiltin : BuiltinTool() {

    override val name: String = "zafiro_env"

    override val description: String =
        "构建环境入口：构建/编译/打包/出APK/工具链/gcc/clang/cmake/ninja/Rust/Go/Node/NDK/env缺失/扩展包解压，先用这个工具，不要先 ls -R 或 find / 摸盘。\n" +
        "Build-environment entry for this device (App build + native toolchain).\n" +
                "action=check   -> list 21 compilers/toolchains (gcc/clang/cmake/ninja/git/node/go/rust/...) with versions\n" +
                "action=build   -> start gradle :app:assembleDebug in background, args passed to build-src.sh; returns log path, poll with action=status\n" +
                "action=status  -> tail of the last build log (BUILD SUCCESSFUL/FAILED + RC)\n" +
                "action=install -> extract an extension pack (target=<abs path to .tar.gz>) into ${ROOT}, then self-check\n" +
                "action=shell   -> run a command inside the Alpine chroot (cmd=...)\n" +
                "action=apk     -> run apk inside the chroot (cmd=add --no-cache jq)\n" +
                "action=snapshot-> pack rootfs into target=<tar.gz>\n" +
                "CALL FORMAT (do NOT hand-craft su/chroot/exec): shell -> {action:\"shell\",cmd:\"gcc --version\"}；不要写 su -c、chroot、cd ${ROOT}，工具内部已处理。\n" +
                "Serialize builds: never start two gradle runs at once (11G RAM, free often <300M)."

    override val defaultEnabled: Boolean = true

    override val inputSchemaJson: String get() = SCHEMA

    override suspend fun invoke(request: BuiltinToolRequest): BuiltinToolResult {
        val obj = try {
            Json.parseToJsonElement(request.argumentsJson).jsonObject
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            return BuiltinToolResult.failure(
                code = "INVALID_ARGUMENTS_JSON",
                message = "zafiro_env arguments must be a JSON object with an action field.",
                hint = """Example: {"action":"check"} or {"action":"build","args":"-PdevSuffix --max-workers=1"}""",
            )
        }
        val action = obj["action"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val args = sanitize(obj["args"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val target = obj["target"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val cmd = sanitize(obj["cmd"]?.jsonPrimitive?.contentOrNull.orEmpty())

        // 固定根目录 ${ROOT}；扩展包默认从公共下载目录 " + PACK_DIR + " 取
        ensureBaseDirs()
        // 环境没装就自动去 " + PACK_DIR + " 找包解压，用户不需要先建目录
        if (!java.io.File(ENV_SH).exists() || action == "deploy") {
            val deployed = deployFromPackDir()
            if (!deployed) {
                return BuiltinToolResult.failure(
                    code = "ENV_NOT_INSTALLED",
                    message = "构建环境未部署：找不到 " + ENV_SH + "。把扩展包（*.tar.gz）放进 " + PACK_DIR + " 后重试，工具会自动解压到 ${ROOT}。",
                    hint = "也可手动：su -c 'tar xzf " + PACK_DIR + "/<包>.tar.gz -C ${ROOT}'",
                )
            }
        }
        val (body, timeoutSeconds) = when (action) {
            "deploy" -> "check" to 120L
            "check" -> "check" to 120L
            "status" -> "status" to 20L
            "build" -> return startBuild(args)
            "install" -> {
                val pack = target.ifBlank { newestPackIn(PACK_DIR) }
                if (pack.isBlank()) return noPackFound()
                "install $pack" to 115L
            }
            "snapshot" -> {
                if (target.isBlank()) return missingField("target")
                "snapshot $target" to 115L
            }
            "shell" -> {
                if (cmd.isBlank()) return missingField("cmd")
                "shell" to 110L
            }
            "apk" -> {
                if (cmd.isBlank()) return missingField("cmd")
                "apk $cmd" to 110L
            }
            else -> return BuiltinToolResult.failure(
                code = "UNKNOWN_ACTION",
                message = "Unknown action '$action'.",
                hint = "Use one of: check, build, status, install, shell, apk, snapshot.",
            )
        }

        val output = if (action == "shell") {
            sh("sh ${ROOT}/env/run.sh /bin/sh -c ${quote(cmd)}", timeoutSeconds)
        } else {
            sh("sh ${ROOT}/env/env.sh $body", timeoutSeconds)
        }
        val ok = !output.contains("[timeout]")
        val payload = JsonObject(
            mapOf("action" to JsonPrimitive(action), "output" to JsonPrimitive(output.takeLast(3500)))
        )
        return if (ok) {
            BuiltinToolResult.success(message = output.takeLast(3500), data = payload)
        } else {
            BuiltinToolResult.failure(code = "TIMEOUT", message = output, data = payload)
        }
    }

    private suspend fun startBuild(args: String): BuiltinToolResult = withContext(Dispatchers.IO) {
        val slots = buildSlots()
        val src = resolveBuildProject()
            ?: return@withContext BuiltinToolResult.failure(
                code = "PROJECT_NOT_FOUND",
                message = "project/ 下没有可构建的项目，源码/ 里也没有可复制的模板。",
                hint = "约定：新项目在 工程/<名字>/ 下创建；模板放 源码/。先确认 ${ROOT}/src/zterm-work 存在，或直接把源码树放到 工程/ 下。",
            )
        val log = "${ROOT}/logs/build-py.log"
        val inner = "cd ${ROOT} && ZAFIRO_BASE=${ROOT} ZAFIRO_A=${ROOT} ZAFIRO_SRC=" + src + " " +
                "sh ${ROOT}/env/env.sh build " + args
        sh(
            "setsid nohup sh ${ROOT}/env/build_queue.sh " + slots + " '" + log + "' " +
                    "sh -c " + quote(inner) + " >/dev/null 2>&1 < /dev/null &",
            20L,
        )
        Thread.sleep(2500)
        val head = sh("head -1 " + log, 10L).trim()
        BuiltinToolResult.success(
            message = "build queued (slots=" + slots + "): " + head + "\nsrc: " + src + "\nlog: " + log,
            data = JsonObject(
                mapOf(
                    "log" to JsonPrimitive(log),
                    "slots" to JsonPrimitive(slots),
                    "src" to JsonPrimitive(src),
                    "state" to JsonPrimitive(head),
                )
            ),
        )
    }

    /** 构建副本：优先 工程/zafiro-ui-h1；否则 工程/ 下除上游外的任意源码树；都没有就从 源码/ 复制一棵。 */
    private suspend fun resolveBuildProject(): String? {
        System.getenv("ZAFIRO_SRC")?.takeIf { it.isNotBlank() }?.let { return it }
        if (java.io.File(BUILD_PROJECT + "/gradlew").exists()) return BUILD_PROJECT
        pickTree("${ROOT}/project")?.let { return it }
        val template = pickTree("${ROOT}/src") ?: return null
        val name = "build-" + java.text.SimpleDateFormat("MMdd-HHmm", java.util.Locale.US).format(java.util.Date())
        val dest = "${ROOT}/project/" + name
        val out = sh(
            "cd " + quote(template) + " && mkdir -p " + quote(dest) + " && " +
                "tar cf - --exclude=./app/build --exclude=./.gradle --exclude=./.kotlin . | (cd " + quote(dest) + " && tar xf -) && echo COPY_OK",
            115L,
        )
        return if (out.contains("COPY_OK")) dest else null
    }

    /** 目录下挑一棵源码树：跳过上游原版（zafiro-14-1.8.0），其余取第一个。 */
    private suspend fun pickTree(dir: String): String? {
        val out = sh("ls -d " + quote(dir) + "/*/gradlew 2>/dev/null", 20L).trim()
        if (out.isBlank()) return null
        val dirs = out.lines().map { it.removeSuffix("/gradlew").trim() }.filter { it.isNotBlank() }
        val usable = dirs.filterNot { val n = it.substringAfterLast("/"); n.startsWith(UPSTREAM_PREFIX) && !n.contains("Fork", ignoreCase = true) }
        return usable.firstOrNull()
    }
    /** 可用内存 > 1.2G 给 2 个并发槽，否则 1 个；绝不因“已有构建在跑”直接拒绝。 */
    private fun buildSlots(): Int {
        val availableKb = runCatching {
            java.io.File("/proc/meminfo").readLines()
                .firstOrNull { it.startsWith("MemAvailable:") }
                ?.filter { it.isDigit() }
                ?.toLong()
        }.getOrNull() ?: 0L
        return if (availableKb > 1_200_000L) 2 else 1
    }

    /** 从公共下载目录 PACK_DIR 里取最新扩展包并解压到 aitools 根目录。 */
    private suspend fun deployFromPackDir(): Boolean {
        val pack = newestPackIn(PACK_DIR)
        if (pack.isBlank()) return false
        ensureBaseDirs()
        val out = sh("tar xzf " + quote(pack) + " -C ${ROOT} && echo DEPLOY_OK", 115L)
        return out.contains("DEPLOY_OK") && java.io.File(ENV_SH).exists()
    }

    /** PACK_DIR 里最新的 *.tar.gz；没有返回空串。走 su，不依赖 App 存储权限。 */
    private suspend fun newestPackIn(dir: String): String {
        // 优先认 zafiro-env*.tar.gz，其次任意 *.tar.gz
        val preferred = sh("ls -t " + quote(dir) + "/zafiro-env*.tar.gz 2>/dev/null | head -1", 20L).trim()
        if (preferred.startsWith("/")) return preferred
        val any = sh("ls -t " + quote(dir) + "/*.tar.gz 2>/dev/null | head -1", 20L).trim()
        return if (any.startsWith("/")) any else ""
    }

    private fun noPackFound() = BuiltinToolResult.failure(
        code = "PACK_NOT_FOUND",
        message = "在 " + PACK_DIR + " 里没找到 *.tar.gz 扩展包。",
        hint = "把 zafiro-env-full-*.tar.gz 放进 " + PACK_DIR + " 再调用 action=install 或 deploy。",
    )

    /** 归一化入参：模型常手搓 su -c / cd 前缀，这里剥掉，只留裸命令。 */
    private fun sanitize(raw: String): String {
        var v = raw.trim()
        while (v.startsWith("su -c ") || v.startsWith("su -c\'") || v == "su -c") {
            v = if (v == "su -c") "" else v.removePrefix("su -c").trim()
        }
        v = v.trim('\'').trim('"')
        listOf("cd ${ROOT} && ", "cd ${ROOT};", "cd ${ROOT} ").forEach {
            v = v.removePrefix(it).trim()
        }
        return v.trim()
    }

    /** 固定目录骨架（幂等，走 su）。 */
    private suspend fun ensureBaseDirs() {
        sh(
            "mkdir -p ${ROOT}/env ${ROOT}/project ${ROOT}/out " +
                    "${ROOT}/logs ${ROOT}/tmp ${ROOT}/src",
            20L,
        )
    }

    /** 有 root 就直接以最高权限跑；没有 root 时普通执行，需要提权的命令明确报错。 */
    private suspend fun sh(command: String, timeoutSeconds: Long): String = withContext(Dispatchers.IO) {
        if (RootAccess.isAvailable()) {
            val rooted = runProcess(listOf("su", "-c", command), timeoutSeconds)
            return@withContext rooted.first
        }
        val plain = runProcess(listOf("sh", "-c", command), timeoutSeconds)
        val denied = plain.first.contains("Permission denied") ||
            plain.first.contains("Operation not permitted") ||
            plain.first.contains("Read-only file system")
        if (denied) {
            return@withContext plain.first + "\n[NO_ROOT] 未获取到 root 权限：该操作需要提权（/data/local 等路径）。"
        }
        return@withContext plain.first
    }

    private fun runProcess(argv: List<String>, timeoutSeconds: Long): Pair<String, Int> {
        val process = runCatching { ProcessBuilder(argv).redirectErrorStream(true).start() }.getOrNull()
            ?: return "[exec-failed] " + argv.joinToString(" ").take(80) to -1
        val output = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return output + "\n[timeout]" to -1
        }
        return output to process.exitValue()
    }
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun missingField(field: String) = BuiltinToolResult.failure(
        code = "MISSING_REQUIRED_FIELD",
        message = "action requires '$field'.",
        fieldErrors = mapOf(field to "Field '$field' must not be blank."),
    )

    private companion object {
        /** 构建/工具根目录：主包 ${ROOT}，测试包派生独立根，互不污染。 */
        val ROOT: String get() = com.hoerire.tools.zafiro.settings.RuntimeEnvironment.aitoolsRoot
        val ENV_SH: String get() = ROOT + "/env/env.sh"
        val BUILD_PROJECT: String get() = ROOT + "/project/zafiro-ui-h1"
        // 上游原版目录名前缀：版本号每代变（14-1.8.0 -> 15-1.8.1），写死完整名会导致 pickTree 跳不过上游
        const val UPSTREAM_PREFIX = "zafiro-1"
        const val PACK_DIR = "/storage/emulated/0/Download"
        const val SCHEMA = """
        {
          "type": "object",
          "properties": {
            "action": {
              "type": "string",
              "enum": ["check", "deploy", "build", "status", "install", "shell", "apk", "snapshot"],
              "description": "check=toolchain versions, build=start gradle in background, status=tail build log, install=extract extension pack, shell=run in chroot, apk=apk in chroot, snapshot=pack rootfs"
            },
            "args": {"type": "string", "description": "gradle args for build, e.g. -PdevSuffix --max-workers=1"},
            "target": {"type": "string", "description": "absolute .tar.gz path for install/snapshot"},
            "cmd": {"type": "string", "description": "command for shell/apk"}
          },
          "required": ["action"]
        }
        """
    }
}
