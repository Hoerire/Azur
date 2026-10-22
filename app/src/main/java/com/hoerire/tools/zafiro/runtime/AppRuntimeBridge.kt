package com.hoerire.tools.zafiro.runtime

import com.hoerire.tools.zafiro.repo.XRepoRuntimeGateway
import com.hoerire.tools.zafiro.settings.RuntimeBridge

fun createAppRuntimeBridge(): RuntimeBridge {
    return RuntimeBridge(
        settings = XRepoRuntimeGateway(),
        host = IpcRuntimeHostGateway(),
    )
}
