package com.hoerire.tools.libterm

sealed interface SshHostKeyPolicy {
    data object AcceptAny : SshHostKeyPolicy

    data class KnownHostsFile(
        val path: String,
        val strict: Boolean = true,
    ) : SshHostKeyPolicy
}
