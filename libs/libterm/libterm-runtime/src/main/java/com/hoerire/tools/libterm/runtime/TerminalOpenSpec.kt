package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.AuthorizationMode
import com.hoerire.tools.libterm.SshOpenOptions
import com.hoerire.tools.libterm.TerminalIdentity

class TerminalOpenSpec {
    lateinit var identity: TerminalIdentity
    var authorizationMode: AuthorizationMode? = null
    var cwd: String? = null
    internal var sshOptions: SshOpenOptions? = null

    fun ssh(configure: SshOpenSpec.() -> Unit) {
        sshOptions = SshOpenSpec().apply(configure).toOpenOptions()
    }

    internal fun hasIdentity(): Boolean = ::identity.isInitialized
}
