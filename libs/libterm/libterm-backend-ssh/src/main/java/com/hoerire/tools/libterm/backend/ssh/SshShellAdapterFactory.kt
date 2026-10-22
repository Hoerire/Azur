package com.hoerire.tools.libterm.backend.ssh

import com.hoerire.tools.libterm.SshOpenOptions

internal interface SshShellAdapterFactory {
    suspend fun open(options: SshOpenOptions): SshShellAdapter
}
