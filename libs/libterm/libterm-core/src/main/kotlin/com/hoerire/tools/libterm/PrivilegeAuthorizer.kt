package com.hoerire.tools.libterm

interface PrivilegeAuthorizer {
    suspend fun requestAuthorization(identity: TerminalIdentity): AuthorizationResult
}
