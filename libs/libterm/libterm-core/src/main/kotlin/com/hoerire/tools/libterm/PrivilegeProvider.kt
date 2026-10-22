package com.hoerire.tools.libterm

interface PrivilegeProvider {
    suspend fun getAvailability(identity: TerminalIdentity): BackendAvailability
}
