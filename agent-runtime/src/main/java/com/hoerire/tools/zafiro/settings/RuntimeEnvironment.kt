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

    /**
     * 宿主工具集的懒部署钩子：App 启动时只注册、不执行。
     * 真正解压发生在首次需要它的工具调用里，避免冷启动为「可能用不到」付代价。
     */
    @Volatile
    private var hostToolsDeployer: (suspend () -> Boolean)? = null

    /** 注册部署器（App 启动时调用，本身不做 IO）。 */
    fun registerHostToolsDeployer(block: suspend () -> Boolean) {
        hostToolsDeployer = block
    }

    /** 幂等：首次调用触发部署，之后走已部署缓存。失败不阻塞工具执行。 */
    suspend fun ensureHostTools(): Boolean {
        val d = hostToolsDeployer ?: return true
        return runCatching { d() }.getOrDefault(false)
    }

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
