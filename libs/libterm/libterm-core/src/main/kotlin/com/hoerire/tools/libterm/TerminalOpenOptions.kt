package com.hoerire.tools.libterm

data class TerminalOpenOptions(
    val cwd: String? = null,
    val ssh: SshOpenOptions? = null,
)
