package com.hoerire.tools.common.util

import com.hoerire.tools.logging.Logger

fun <T> xTry(
    name: String = "xTry",
    block: () -> T
): T? = runCatching(block).onFailure {
    Logger.w(LOG_TAG, "$name failed: ${it.message}")
}.getOrNull()

private const val LOG_TAG = "zafiro_xTry"
