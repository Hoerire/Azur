package com.hoerire.tools.libterm.backend.libsu

import com.hoerire.tools.libterm.BackendAvailability
import com.hoerire.tools.libterm.PrivilegeProvider
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity

class LibsuPrivilegeProvider internal constructor(
    private val rootAccessChecker: LibsuRootAccessChecker,
) : PrivilegeProvider {
    public constructor() : this(RealLibsuRootAccessChecker())

    override suspend fun getAvailability(identity: TerminalIdentity): BackendAvailability {
        return when (identity) {
            TerminalIdentity.User -> BackendAvailability.Available
            TerminalIdentity.Su -> getRootAvailability()
            TerminalIdentity.Shizuku -> BackendAvailability.Unavailable(
                TerminalFailure.BackendUnavailable(
                    identity = TerminalIdentity.Shizuku,
                    message = "libsu backend does not support SHIZUKU",
                ),
            )

            TerminalIdentity.Ssh -> BackendAvailability.Unavailable(
                TerminalFailure.BackendUnavailable(
                    identity = TerminalIdentity.Ssh,
                    message = "libsu backend does not support SSH",
                ),
            )
        }
    }

    private suspend fun getRootAvailability(): BackendAvailability {
        return when (val result = rootAccessChecker.checkRootAccess()) {
            LibsuRootAccessResult.Available -> BackendAvailability.Available
            is LibsuRootAccessResult.Unavailable -> BackendAvailability.Unavailable(
                TerminalFailure.BackendUnavailable(
                    identity = TerminalIdentity.Su,
                    message = result.message ?: "Root access is not available",
                ),
            )

            is LibsuRootAccessResult.Unauthorized -> BackendAvailability.Unauthorized(
                TerminalFailure.AuthorizationDenied(
                    identity = TerminalIdentity.Su,
                    message = result.message ?: "Root authorization was denied",
                ),
            )
        }
    }
}
