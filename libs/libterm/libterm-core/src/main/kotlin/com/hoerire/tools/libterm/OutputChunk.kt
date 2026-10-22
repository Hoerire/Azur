package com.hoerire.tools.libterm

data class OutputChunk(
    val stream: OutputStream,
    val bytes: TerminalBytes,
    val timestampMillis: Long,
)
