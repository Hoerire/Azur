package com.hoerire.tools.libterm

sealed interface SendResult {
    data object Sent : SendResult

    data class Failed(val failure: TerminalFailure) : SendResult
}
