package com.hoerire.tools.libterm.runtime

interface TerminalOutputDecoder {
    fun createSessionDecoder(): SessionTerminalOutputDecoder
}
