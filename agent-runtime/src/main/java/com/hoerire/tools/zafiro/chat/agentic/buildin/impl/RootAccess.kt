package com.hoerire.tools.zafiro.chat.agentic.buildin.impl

import java.util.concurrent.TimeUnit

/**
 * root 检测：能拿到 su 就直接以最高权限跑；拿不到就退回普通用户，
 * 需要提权的命令由调用方明确报「未获取到 root 权限」，不静默降级。
 */
object RootAccess {

    @Volatile
    private var cached: Boolean? = null

    fun isAvailable(): Boolean {
        cached?.let { return it }
        val result = runCatching {
            val process = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            finished && output.lineSequence().any { it.trim() == "0" }
        }.getOrDefault(false)
        cached = result
        return result
    }

    fun reset() {
        cached = null
    }
}
