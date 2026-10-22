package com.hoerire.tools.libterm

sealed interface BackendStartResult {
    data object Started : BackendStartResult

    data class Failed(
        val failure: TerminalFailure,
    ) : BackendStartResult
}
