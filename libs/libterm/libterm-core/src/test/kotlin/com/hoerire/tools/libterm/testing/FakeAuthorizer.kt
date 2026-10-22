package com.hoerire.tools.libterm.testing

import com.hoerire.tools.libterm.AuthorizationResult
import com.hoerire.tools.libterm.PrivilegeAuthorizer
import com.hoerire.tools.libterm.TerminalFailure
import com.hoerire.tools.libterm.TerminalIdentity

class FakeAuthorizer : PrivilegeAuthorizer {
    private val resultsByIdentity = mutableMapOf<TerminalIdentity, AuthorizationResult>()

    val requestedIdentities = mutableListOf<TerminalIdentity>()

    override suspend fun requestAuthorization(identity: TerminalIdentity): AuthorizationResult {
        requestedIdentities += identity
        return resultsByIdentity[identity] ?: AuthorizationResult.Granted
    }

    fun setGranted(identity: TerminalIdentity) {
        resultsByIdentity[identity] = AuthorizationResult.Granted
    }

    fun setDenied(identity: TerminalIdentity, message: String? = null) {
        resultsByIdentity[identity] = AuthorizationResult.Denied(
            TerminalFailure.AuthorizationDenied(identity = identity, message = message),
        )
    }
}
