package com.hoerire.tools.libterm.runtime.internal

import android.content.Context
import com.hoerire.tools.libterm.Clock
import com.hoerire.tools.libterm.TerminalBackend
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.TerminalOpenOptions
import com.hoerire.tools.libterm.backend.libsu.LibsuTerminalBackend
import com.hoerire.tools.libterm.backend.shizuku.ShizukuTerminalBackend
import com.hoerire.tools.libterm.backend.ssh.SshTerminalBackend
import kotlinx.coroutines.CoroutineScope

internal class RuntimeBackendFactory(
    context: Context,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val appContext: Context = context.applicationContext ?: context

    internal fun create(
        identity: TerminalIdentity,
        openOptions: TerminalOpenOptions,
    ): TerminalBackend {
        return when (identity) {
            TerminalIdentity.User -> LibsuTerminalBackend(
                identity = TerminalIdentity.User,
                clock = clock,
                scope = scope,
            )

            TerminalIdentity.Su -> LibsuTerminalBackend(
                identity = TerminalIdentity.Su,
                clock = clock,
                scope = scope,
            )

            TerminalIdentity.Shizuku -> ShizukuTerminalBackend(
                context = appContext,
                clock = clock,
                scope = scope,
            )

            TerminalIdentity.Ssh -> SshTerminalBackend(
                options = requireNotNull(openOptions.ssh) {
                    "SSH open options are required"
                },
                clock = clock,
                scope = scope,
            )
        }
    }
}
