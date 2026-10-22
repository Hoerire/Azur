package com.hoerire.tools.libterm.backend.ssh

import com.hoerire.tools.libterm.TerminalBytes
import kotlinx.coroutines.flow.Flow

internal interface SshShellAdapter {
    val output: Flow<TerminalBytes>

    suspend fun write(input: TerminalBytes)

    suspend fun close()

    suspend fun awaitExit(): Throwable?
}
