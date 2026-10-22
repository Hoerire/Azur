package com.hoerire.tools.zafiro.settings.model

/**
 * 单次输出上限默认值（max_output_tokens / max_tokens）。
 * 旧默认是 okia 骨架里的 4096，会把长回答和带大参数的工具调用从中间切断
 * （截断的工具调用无法执行，是「工具块一直转圈」的根因）。
 */
const val DEFAULT_MAX_TOKENS: Int = 128_000

/**
 * 上下文窗口默认值（环的分母）。配置里留空时用它。
 * 注意：默认值大于模型真实窗口时，环会显示偏小的占用，压缩门（0.85）迟迟不触发——
 * 所以真实窗口应当填进配置。
 */
const val DEFAULT_CONTEXT_WINDOW_TOKENS: Long = 1_000_000L

data class RuntimeLlmConfig(
    val provider: String = "",
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** LlmProtocol.wireId，如 "openai-responses"。空串回落默认协议。 */
    val protocol: String = "",
    /** 视觉模型开关（图片输入支持），默认关。 */
    val supportsImages: Boolean = false,
    val prompt: String = "",
    val proxy: String = "",
    val memoryPrompt: String = "",
    val memories: List<String> = emptyList(),
    /** 流式空闲超时秒数；null = 不超时（Long.MAX_VALUE）。 */
    val idleTimeoutSeconds: Long? = 60L,
    /** 传输层自动重试次数。 */
    val retryMaxAttempts: Int = 3,
    /** 单次输出上限（max_output_tokens / max_tokens）。 */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    /** 上下文窗口（分母）；≤0 由 gateway 落 [DEFAULT_CONTEXT_WINDOW_TOKENS]。 */
    val contextWindowTokens: Long = DEFAULT_CONTEXT_WINDOW_TOKENS,
    /** ThinkingLevel.wireValue；空串 = 不发送思考字段（Provider 默认行为）。 */
    val thinkingLevel: String = "",
    /** 复读检测灵敏度档位；见 [RepetitionSensitivity]。 */
    val repetitionSensitivity: RepetitionSensitivity = RepetitionSensitivity.DEFAULT,
    /** 上下文自动压缩阈值百分比（例如 70 表示 70%，0 表示关闭）。 */
    val compactionThresholdPercent: Int = 70,
)

enum class RuntimeAgentMemoryMode {
    Disabled,
    SharedMain,
}

data class RuntimeAgentProfile(
    val id: String,
    val name: String,
    val alias: String,
    val enabled: Boolean = true,
    val order: Int = 0,
    val memoryMode: RuntimeAgentMemoryMode = RuntimeAgentMemoryMode.SharedMain,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class RuntimeAgentValidation(
    val field: String,
    val message: String,
)

data class RuntimeSkillMetadata(
    val id: String,
    val name: String,
    val description: String,
    val relativePath: String,
    val absolutePath: String,
    val absoluteDir: String,
    val enabled: Boolean,
)

data class RuntimeLoadedSkill(
    val id: String,
    val name: String,
    val description: String,
    val relativePath: String,
    val absolutePath: String,
    val absoluteDir: String,
    val content: String,
    val enabled: Boolean,
)

data class RuntimeSkillValidation(
    val field: String,
    val message: String,
)

/**
 * 持久化 Python 工具（由 custom_py_tools 创建）条目。
 * code 是工具本体；description/schemaJson 是反射结果的缓存，代码变更后重算覆盖。
 */
data class RuntimeCustomPyTool(
    val name: String,
    val code: String,
    val description: String = "",
    val schemaJson: String = "",
    val enabled: Boolean = true,
    val timeoutMs: Long = DEFAULT_CUSTOM_PY_TOOL_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_CUSTOM_PY_TOOL_TIMEOUT_MS: Long = 30_000L
        const val MIN_CUSTOM_PY_TOOL_TIMEOUT_MS: Long = 1_000L
        const val MAX_CUSTOM_PY_TOOL_TIMEOUT_MS: Long = 120_000L
    }
}

data class RuntimeBuiltinToolSetting(
    val name: String,
    val description: String,
    val enabled: Boolean,
)

enum class RuntimeExecutionRuleEnabledMode {
    ALWAYS,
    LOCKED_ONLY,
    DISABLED,

    /** 命中后挂起等待用户确认；无确认渠道（宿主路径）时拒绝。 */
    CONFIRM,
}

data class RuntimeExecutionRule(
    val id: String,
    val name: String,
    val enabledMode: RuntimeExecutionRuleEnabledMode,
    val patterns: List<String>,
    /** 空 = 对所有工具生效；非空 = 只在列出的工具上匹配。 */
    val toolFilter: List<String> = emptyList(),
    /** 命中后回给模型的「正确做法」提示，直接省一次试错。 */
    val hint: String = "",
)

enum class RuntimeTakeoverTarget {
    NATIVE_ASSISTANT,
    ZAFIRO,
}

data class RuntimeTakeoverRule(
    val id: String,
    val name: String,
    val target: RuntimeTakeoverTarget,
    val enabled: Boolean = true,
    val patterns: List<String>,
)

const val TAKEOVER_FIELD_NAME: String = "name"
const val TAKEOVER_FIELD_PATTERNS: String = "patterns"

/** 工具校验错误的来源：Code = 用户代码/配置问题，Internal = 应用内部问题。UI 据此选择提示文案。 */
enum class RuntimeToolValidationOrigin {
    Code,
    Internal,
}

data class RuntimeToolValidation(
    val field: String,
    val message: String,
    val origin: RuntimeToolValidationOrigin = RuntimeToolValidationOrigin.Code,
)

