package com.hoerire.tools.zafiro.chat.agentic

import com.hoerire.tools.logging.Logger
import com.hoerire.tools.zafiro.chat.LocalTool
import com.hoerire.tools.zafiro.chat.ResolvedTools
import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinTool
import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinToolRegistry
import com.hoerire.tools.zafiro.settings.model.RuntimeCustomPyTool
import com.hoerire.tools.zafiro.settings.model.RuntimeBuiltinToolSetting as BuiltinToolSetting

class ToolManager(
    private val builtinToolRegistry: BuiltinToolRegistry = BuiltinToolRegistry.default(),
) {
    private companion object {
        const val LOG_TAG = "zafiro_ToolManager"
    }

    fun resolve(
        customPyTools: List<RuntimeCustomPyTool>,
        builtinSettings: List<BuiltinToolSetting>,
    ): ResolvedTools {
        val builtinTools = buildBuiltinTools(builtinSettings)
        val pyRuntimeTools = buildCustomPyTools(customPyTools)

        Logger.d(
            LOG_TAG,
            "tools resolve builtin=${builtinTools.size} py=${pyRuntimeTools.size} " +
                    "input builtinSettings=${builtinSettings.size} customPyTools=${customPyTools.size}"
        )

        return ResolvedTools(
            builtinTools = builtinTools,
            customPyTools = pyRuntimeTools,
        )
    }

    private fun buildBuiltinTools(settings: List<BuiltinToolSetting>): List<LocalTool.Builtin> {
        return settings
            .filter { it.enabled }
            .sortedBy { it.name }
            .mapNotNull { setting ->
                val tool = findBuiltinTool(setting.name) ?: return@mapNotNull null
                LocalTool.Builtin(
                    name = setting.name,
                    description = setting.description,
                    tool = tool,
                )
            }
    }

    private fun findBuiltinTool(name: String): BuiltinTool? {
        return builtinToolRegistry.find(name)
            ?: builtinToolRegistry.all().firstOrNull { it::class.simpleName == name }
    }

    // Python 运行时已移除：自定义 Python 工具不再注入工具表（存量数据仍留在设置里，
    // 只在 ToolSettingsCodec 层保留编解码以兼容旧 JSON）。
    private fun buildCustomPyTools(tools: List<RuntimeCustomPyTool>): List<LocalTool.Py> =
        emptyList()

}
