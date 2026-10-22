package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.TerminalBytes

data class CommandResult(
    val command: String,
    val stdout: TerminalBytes,
    val stderr: TerminalBytes,
    val exitCode: Int?,
    val timedOut: Boolean,
    val terminationReason: CommandTerminationReason = CommandTerminationReason.COMPLETED,
)
