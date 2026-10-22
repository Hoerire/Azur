package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.OutputStream

data class TerminalTextChunk(
    val stream: OutputStream,
    val text: String,
    val timestampMillis: Long,
)
