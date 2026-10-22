package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.OutputChunk

interface SessionTerminalOutputDecoder {
    fun decode(chunk: OutputChunk): List<TerminalTextChunk>

    fun reset()
}
