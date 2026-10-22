package com.hoerire.tools.libterm

sealed interface SshAuth {
    data class Password(
        val value: String,
    ) : SshAuth

    data class PrivateKey(
        val privateKeyPem: String,
        val passphrase: String? = null,
    ) : SshAuth
}
