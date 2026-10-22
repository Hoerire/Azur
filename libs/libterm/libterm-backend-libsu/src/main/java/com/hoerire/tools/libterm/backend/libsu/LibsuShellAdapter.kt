package com.hoerire.tools.libterm.backend.libsu

import com.hoerire.tools.libterm.SendResult
import com.hoerire.tools.libterm.TerminalBytes
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.TerminalOpenOptions
import kotlinx.coroutines.flow.Flow

internal interface LibsuShellAdapterFactory {
    suspend fun open(
        identity: TerminalIdentity,
        openOptions: TerminalOpenOptions,
    ): LibsuShellSession
}

internal class LibsuInvalidOpenOptionsException(
    val failure: TerminalFailure.InvalidOpenOptions,
) : IllegalStateException(failure.message)

internal interface LibsuShellSession {
    val output: Flow<LibsuOutputEvent>

    suspend fun write(input: TerminalBytes): SendResult

    suspend fun close()

    suspend fun awaitExit(): TerminalFailure?
}

internal sealed interface LibsuOutputEvent {
    data class Stdout(val bytes: TerminalBytes) : LibsuOutputEvent

    data class Stderr(val bytes: TerminalBytes) : LibsuOutputEvent
}
