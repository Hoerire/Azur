package com.hoerire.tools.libterm.runtime.internal

import com.hoerire.tools.libterm.OutputChunk
import com.hoerire.tools.libterm.runtime.SessionTerminalOutputDecoder
import com.hoerire.tools.libterm.runtime.TerminalTextChunk

internal class LambdaSessionOutputDecoder(
    private val decodeText: (OutputChunk) -> String,
) : SessionTerminalOutputDecoder {
    override fun decode(chunk: OutputChunk): List<TerminalTextChunk> {
        return listOf(
            TerminalTextChunk(
                stream = chunk.stream,
                text = decodeText(chunk),
                timestampMillis = chunk.timestampMillis,
            ),
        )
    }

    override fun reset() = Unit
}
