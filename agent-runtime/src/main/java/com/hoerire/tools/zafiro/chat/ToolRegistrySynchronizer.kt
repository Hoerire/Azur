package com.hoerire.tools.zafiro.chat

import com.hoerire.tools.logging.Logger
import com.hoerire.tools.okia.tooling.DefaultToolRegistry
import com.hoerire.tools.okia.tooling.ToolDescriptor
import com.hoerire.tools.okia.tooling.ToolKind
import com.hoerire.tools.okia.tooling.ToolRegistry
import com.hoerire.tools.zafiro.api.AgentControl
import com.hoerire.tools.zafiro.chat.agentic.LocalToolExecutor

internal class ToolRegistrySynchronizer(
    private val tenantId: String = "",
    private val agentControlProvider: (() -> AgentControl?)? = null,
    private val currentTools: () -> ResolvedTools?,
) {
    private companion object {
        const val LOG_TAG = "zafiro_ToolRegistrySynchronizer"
    }

    val toolRegistry: ToolRegistry = DefaultToolRegistry()


    private val localToolExecutor = LocalToolExecutor(
        currentTools = currentTools,
        tenantId = tenantId,
        agentControlProvider = agentControlProvider,
    )

    fun reset() {
        toolRegistry.snapshot().forEach { toolRegistry.remove(it.descriptor.wireName) }
    }

    /**
     * 全量重建本地工具注册：registry 中所有 Local 工具先移除（含 inline 的），
     * 再注册当前 resolved 的 enabled 工具。
     */
    fun syncLocalTools(tools: ResolvedTools) {
        toolRegistry.snapshot()
            .map { it.descriptor }
            .filter { it.kind is ToolKind.Local }
            .forEach { toolRegistry.remove(it.wireName) }
        (tools.builtinTools + tools.customPyTools).forEach { tool ->
            val inputSchemaJson = when (tool) {
                is LocalTool.Builtin -> tool.tool.inputSchemaJson
                is LocalTool.Py -> tool.inputSchemaJson
            }
            toolRegistry.register(
                ToolDescriptor(
                    name = tool.name,
                    description = tool.description,
                    inputSchemaJson = inputSchemaJson,
                    kind = ToolKind.Local,
                ),
                localToolExecutor,
            )
        }
    }


}
