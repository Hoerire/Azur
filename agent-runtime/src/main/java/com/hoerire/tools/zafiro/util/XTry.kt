package com.hoerire.tools.zafiro.util

import com.hoerire.tools.logging.Logger

/** 吞异常 + 记一行警告，失败返回 null（原 xposed-api 的 xTry）。 */
fun <T> xTry(
    name: String = "xTry",
    block: () -> T,
): T? = runCatching(block).onFailure {
    Logger.w(LOG_TAG, "$name failed: ${it.message}")
}.getOrNull()

private const val LOG_TAG = "zafiro_xTry"
