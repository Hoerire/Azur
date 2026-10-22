package com.hoerire.tools.zafiro.settings

import kotlinx.coroutines.CompletableDeferred

object RuntimeEnvironment {
    @Volatile
    private var bridge: RuntimeBridge? = null

    @Volatile
    private var bridgeReady = CompletableDeferred<RuntimeBridge>()

    /** hosttools 自检结论（ok / exec_denied / missing / fail），由 app 冷启动写入。 */
    @Volatile
    var hostToolsStatus: String? = null

    /** 构建/工具根目录：主包 /data/local/aitools，测试包 /data/local/aitools-<后缀>。 */
    @Volatile
    var aitoolsRoot: String = "/data/local/aitools"

    fun install(bridge: RuntimeBridge) {
        this.bridge = bridge
        if (!bridgeReady.isCompleted) {
            bridgeReady.complete(bridge)
        }
    }

    fun requireBridge(): RuntimeBridge {
        return bridge
            ?: error("RuntimeBridge is not installed.")
    }

    suspend fun awaitBridge(): RuntimeBridge {
        bridge?.let { return it }
        return bridgeReady.await()
    }

    fun requireSettingsGateway(): RuntimeSettingsGateway {
        return requireBridge().settings
    }

    suspend fun awaitSettingsGateway(): RuntimeSettingsGateway {
        return awaitBridge().settings
    }

    fun clearForTest() {
        bridge = null
        bridgeReady = CompletableDeferred()
    }
}
