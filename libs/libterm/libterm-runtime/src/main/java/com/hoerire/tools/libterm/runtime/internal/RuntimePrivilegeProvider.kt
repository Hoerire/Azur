package com.hoerire.tools.libterm.runtime.internal

import com.hoerire.tools.libterm.BackendAvailability
import com.hoerire.tools.libterm.PrivilegeProvider
import com.hoerire.tools.libterm.TerminalIdentity
import com.hoerire.tools.libterm.backend.libsu.LibsuPrivilegeProvider
import com.hoerire.tools.libterm.backend.shizuku.ShizukuPrivilegeProvider

internal class RuntimePrivilegeProvider(
    private val libsuProvider: PrivilegeProvider = LibsuPrivilegeProvider(),
    private val shizukuProvider: PrivilegeProvider = ShizukuPrivilegeProvider(),
) : PrivilegeProvider {
    override suspend fun getAvailability(identity: TerminalIdentity): BackendAvailability {
        return when (identity) {
            TerminalIdentity.User,
            TerminalIdentity.Su -> libsuProvider.getAvailability(identity)

            TerminalIdentity.Shizuku -> shizukuProvider.getAvailability(identity)

            TerminalIdentity.Ssh -> BackendAvailability.Available
        }
    }
}
