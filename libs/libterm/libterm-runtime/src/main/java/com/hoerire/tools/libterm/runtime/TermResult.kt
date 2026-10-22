package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.TerminalFailure

sealed interface TermResult<out T> {
    data class Success<T>(
        val value: T,
    ) : TermResult<T>

    data class Failure(
        val failure: TerminalFailure,
    ) : TermResult<Nothing>
}
