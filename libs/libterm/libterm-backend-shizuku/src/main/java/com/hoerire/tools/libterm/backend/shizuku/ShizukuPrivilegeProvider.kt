package com.hoerire.tools.libterm.backend.shizuku

import com.hoerire.tools.libterm.BackendAvailability
import com.hoerire.tools.libterm.PrivilegeProvider
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.backend.shizuku.internal.RealShizukuAccessChecker
import com.hoerire.tools.libterm.backend.shizuku.internal.ShizukuAccessChecker
import com.hoerire.tools.libterm.backend.shizuku.internal.ShizukuAccessState

class ShizukuPrivilegeProvider internal constructor(
    private val accessChecker: ShizukuAccessChecker,
) : PrivilegeProvider {
    public constructor() : this(RealShizukuAccessChecker())

    override suspend fun getAvailability(identity: TerminalIdentity): BackendAvailability {
        return when (identity) {
            TerminalIdentity.Shizuku -> getShizukuAvailability()
            TerminalIdentity.User,
            TerminalIdentity.Su,
            TerminalIdentity.Ssh -> BackendAvailability.Unavailable(
                TerminalFailure.BackendUnavailable(
                    identity = identity,
                    message = "Shizuku backend only supports SHIZUKU",
                ),
            )
        }
    }

    private suspend fun getShizukuAvailability(): BackendAvailability {
        return when (accessChecker.checkAccess()) {
            ShizukuAccessState.NotInstalledOrNotRunning -> BackendAvailability.Unavailable(
                TerminalFailure.BackendUnavailable(
                    identity = TerminalIdentity.Shizuku,
                    message = "Shizuku is not installed or not running",
                ),
            )

            ShizukuAccessState.Unauthorized -> BackendAvailability.Unauthorized(
                TerminalFailure.AuthorizationDenied(
                    identity = TerminalIdentity.Shizuku,
                    message = "Shizuku authorization was denied",
                ),
            )

            ShizukuAccessState.Authorized -> BackendAvailability.Available
        }
    }
}
