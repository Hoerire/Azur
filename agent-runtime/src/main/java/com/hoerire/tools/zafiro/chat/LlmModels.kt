package com.hoerire.tools.zafiro.chat

import com.hoerire.tools.okia.message.ThinkingLevel
import com.hoerire.tools.zafiro.chat.agentic.PromptComposeResult
import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinTool
import com.hoerire.tools.zafiro.settings.model.RepetitionSensitivity

data class LlmRuntimeSnapshot(
    val config: ResolvedLlmConfig,
    val tools: ResolvedTools,
    val prompt: PromptComposeResult,
)

data class ResolvedLlmConfig(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val baseSystemPrompt: String,
    val finalSystemPrompt: String,
    val proxy: String = "",
    val supportsImages: Boolean = false,
    val idleTimeoutSeconds: Long? = 60L,
    val retryMaxAttempts: Int = 3,
    val maxTokens: Int,
    /** 思考强度；null = 不发送思考字段（Provider 默认行为）。 */
    val thinkingLevel: ThinkingLevel? = null,
    /** 复读检测灵敏度档位。 */
    val repetitionSensitivity: RepetitionSensitivity = RepetitionSensitivity.DEFAULT,
)

data class ResolvedTools(
    val builtinTools: List<LocalTool> = emptyList(),
    val customPyTools: List<LocalTool> = emptyList(),
)

sealed interface LocalTool {
    val name: String
    val description: String

    data class Builtin(
        override val name: String,
        override val description: String,
        val tool: BuiltinTool,
    ) : LocalTool

    /**
     * 持久化 Python 工具：code 在 ：python 进程执行，参数 schema 来自签名反射缓存。
     */
    data class Py(
        override val name: String,
        override val description: String,
        val code: String,
        val inputSchemaJson: String?,
        val timeoutMs: Long = 30_000L,
    ) : LocalTool
}

enum class ToolParameterType {
    String,
    Int,
    Boolean,
    Number,
    Object,
    Array,
}

fun ResolvedTools.allLocalTools(): List<LocalTool> {
    return builtinTools + customPyTools
}

fun ResolvedTools.allLocalToolNames(): List<String> {
    return allLocalTools().map { it.name }
}

