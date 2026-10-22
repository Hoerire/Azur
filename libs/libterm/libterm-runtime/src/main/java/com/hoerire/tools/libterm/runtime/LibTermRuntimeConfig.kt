package com.hoerire.tools.libterm.runtime

import com.hoerire.tools.libterm.AuthorizationMode
import com.hoerire.tools.libterm.OutputChunk

class LibTermRuntimeConfig {
    var defaultAuthorizationMode: AuthorizationMode = AuthorizationMode.REQUEST_IF_NEEDED
    var defaultCwd: String? = null
    var outputDecoder: TerminalOutputDecoder = AnsiStrippingTerminalOutputDecoder()
    var outputDecode: ((OutputChunk) -> String)? = null
}
