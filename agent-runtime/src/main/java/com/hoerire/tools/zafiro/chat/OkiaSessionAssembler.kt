package com.hoerire.tools.zafiro.chat

import com.hoerire.tools.logging.Logger
import com.hoerire.tools.okia.ImageSaver
import com.hoerire.tools.okia.Okia
import com.hoerire.tools.okia.TurnOptions
import com.hoerire.tools.okia.conversation.Conversation
import com.hoerire.tools.okia.conversation.SessionSnapshot
import com.hoerire.tools.okia.error.RetryPolicy
import com.hoerire.tools.okia.event.TurnEvent
import com.hoerire.tools.okia.hooks.Hooks
import com.hoerire.tools.okia.hooks.SerializationHolder
import com.hoerire.tools.okia.loop.RequestLogger
import com.hoerire.tools.okia.message.ContentBlock
import com.hoerire.tools.okia.message.Message
import com.hoerire.tools.okia.message.ToolCallOutcome
import com.hoerire.tools.okia.protocol.AnthropicMessagesProtocol
import com.hoerire.tools.okia.protocol.ChatProtocol
import com.hoerire.tools.okia.protocol.GoogleOpenAiCompat
import com.hoerire.tools.okia.protocol.OpenAIChatCompletionCompat
import com.hoerire.tools.okia.protocol.OpenAIChatCompletionProtocol
import com.hoerire.tools.okia.protocol.OpenAIResponsesProtocol
import com.hoerire.tools.okia.tooling.ToolRegistry
import com.hoerire.tools.okia.transport.HttpRequest
import com.hoerire.tools.zafiro.chat.agentic.AndroidImageLoader
import com.hoerire.tools.zafiro.chat.agentic.shell.SessionRegistry
import com.hoerire.tools.zafiro.chat.agentic.shell.TerminalSessionPool
import com.hoerire.tools.zafiro.chat.compaction.ContextPruner
import com.hoerire.tools.zafiro.settings.model.LlmProtocol
import com.hoerire.tools.zafiro.settings.model.RuntimeLlmConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 孤儿工具调用兜底文案：只说结果缺失与疑似异常中断，不断言进程被杀。 */
private const val TOOL_RESULT_MISSING =
    "Tool result missing: execution may have been interrupted abnormally."

internal class LlmConfigRequiredException : IllegalStateException("LLM config is required")

/**
 * Okia 实例装配器与生命周期持有者。
 *
 * 负责协议解析、OkiaConfig 组装、Hooks 挂载以及会话流转发。
 */
internal class OkiaSessionAssembler(
    private val toolRegistry: ToolRegistry,
    private val imageLoader: AndroidImageLoader?,
    private val ensureImageSaver: suspend () -> ImageSaver?,
) {
    internal companion object {
        const val LOG_TAG = "zafiro_OkiaSessionAssembler"

        /**
         * 请求级诊断：每段 HTTP 请求一行。出问题时用来区分「一个请求在复读」
         * 与「app 在反复重发」——okia 无 Logger，由宿主注入实现。
         */
        val androidRequestLogger = object : RequestLogger {
            override fun started(segment: Int, attempt: Int) {
                Logger.i(LOG_TAG, "request start segment=$segment attempt=$attempt")
            }

            override fun completed(
                segment: Int,
                attempt: Int,
                elapsedMs: Long,
                stopReason: String?,
                failed: Boolean,
            ) {
                Logger.i(
                    LOG_TAG,
                    "request done segment=$segment attempt=$attempt elapsedMs=$elapsedMs " +
                            "stopReason=${stopReason ?: "null"} failed=$failed"
                )
            }
        }

        internal val compactPruningHook = object : Hooks {
            override suspend fun beforeSerialization(request: SerializationHolder) {
                val outcome = ContextPruner.prune(request.history)
                if (outcome.hasCompacted) {
                    request.write(request.snapshot, outcome.messages, "compact_prune")
                    Logger.i(
                        LOG_TAG,
                        "compactPruningHook pruned: ${request.history.size} -> ${outcome.messages.size} (dropped ${outcome.prunedCount})"
                    )
                }
            }
        }

        /**
         * 工具回执预算：单条超过 head+tail 时掐中段。
         *
         * 一次 python/grep 输出原样回灌就能吃掉十几 K token，而模型多数只需要
         * 开头（结构）和结尾（错误/结果）。改写只发生在请求历史副本上
         * （[SerializationHolder.write] 一次性，不写回会话树），UI 与落盘保持完整。
         */
        internal val toolResultBudgetHook = object : Hooks {
            override suspend fun beforeSerialization(request: SerializationHolder) {
                var hit = 0
                var saved = 0
                val rewritten = request.history.map { message ->
                    if (message !is Message.ToolResult) return@map message
                    val content = toolResultContent(message.outcome) ?: return@map message
                    if (content.length <= TOOL_RESULT_KEEP_CHARS) return@map message
                    val head = content.take(TOOL_RESULT_HEAD_CHARS)
                    val tail = content.takeLast(TOOL_RESULT_TAIL_CHARS)
                    val dropped = content.length - head.length - tail.length
                    val trimmed = head + "\n\n…[已截断 ${dropped} 字符：输出过长，需要时用范围参数重取]…\n\n" + tail
                    hit++
                    saved += dropped
                    message.copy(outcome = withToolResultContent(message.outcome, trimmed))
                }
                if (hit > 0) {
                    request.write(request.snapshot, rewritten, "tool_result_budget")
                    Logger.i(LOG_TAG, "toolResultBudget hit=$hit savedChars=$saved")
                }
            }
        }

        /** 请求体大小：固定开销（工具 schema 等）不在 systemPrompt 里，只能从 body 量。 */
        internal val requestSizeLogHook = object : Hooks {
            override suspend fun afterRequest(request: HttpRequest) {
                val chars = request.body?.length ?: 0
                Logger.i(LOG_TAG, "request body chars=$chars estTokens=${(chars / 3.5).toLong()}")
            }
        }

        private const val TOOL_RESULT_HEAD_CHARS = 6_000
        private const val TOOL_RESULT_TAIL_CHARS = 1_500
        private const val TOOL_RESULT_KEEP_CHARS = TOOL_RESULT_HEAD_CHARS + TOOL_RESULT_TAIL_CHARS + 200

        private fun toolResultContent(outcome: ToolCallOutcome): String? = when (outcome) {
            is ToolCallOutcome.Success -> outcome.content
            is ToolCallOutcome.Failure -> outcome.content
            is ToolCallOutcome.Intercepted -> outcome.content
            is ToolCallOutcome.Interrupted -> outcome.content
            is ToolCallOutcome.Unknown -> outcome.content
        }

        private fun withToolResultContent(
            outcome: ToolCallOutcome,
            text: String,
        ): ToolCallOutcome = when (outcome) {
            is ToolCallOutcome.Success -> outcome.copy(content = text)
            is ToolCallOutcome.Failure -> outcome.copy(content = text)
            is ToolCallOutcome.Intercepted -> outcome.copy(content = text)
            is ToolCallOutcome.Interrupted -> outcome.copy(content = text)
            is ToolCallOutcome.Unknown -> outcome.copy(content = text)
        }

    }

    var activeSession: Okia? = null
    var sessionProtocol: LlmProtocol? = null
        private set

    private val conversationForwardScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val conversationFlow = MutableStateFlow<Conversation?>(null)
    private var sessionForwardJob: Job? = null

    val currentConversation: StateFlow<Conversation?> get() = conversationFlow

    var okiaFactory: AgentSessionEngine.OkiaFactory =
        AgentSessionEngine.OkiaFactory { protocol, restore, config ->
            openOkiaWithDefaultProtocol(protocol, restore, config)
        }

    fun resetForTest() {
        kotlinx.coroutines.runBlocking { activeSession?.close() }
        activeSession = null
        sessionProtocol = null
        sessionForwardJob?.cancel()
        sessionForwardJob = null
        conversationFlow.value = null
        okiaFactory = AgentSessionEngine.OkiaFactory { protocol, restore, config ->
            openOkiaWithDefaultProtocol(protocol, restore, config)
        }
    }

    suspend fun obtainSession(
        protocol: LlmProtocol?,
        config: ResolvedLlmConfig,
        restore: SessionSnapshot? = null,
        forceNew: Boolean = false,
    ): Okia {
        if (!forceNew && restore == null) {
            activeSession?.takeIf { sessionProtocol == protocol }?.let { return it }
        }
        val carried = restore ?: activeSession?.takeIf { sessionProtocol != protocol }?.export()
        activeSession?.close()
        return openSession(protocol ?: LlmProtocol.Default, config, carried).also {
            activeSession = it
            sessionProtocol = protocol
            forwardConversation(it)
        }
    }

    private suspend fun openSession(
        protocol: LlmProtocol,
        config: ResolvedLlmConfig,
        restore: SessionSnapshot?,
    ): Okia = okiaFactory.create(protocol, restore, config)

    private fun forwardConversation(session: Okia) {
        sessionForwardJob?.cancel()
        sessionForwardJob = conversationForwardScope.launch {
            session.conversation.collect { conversationFlow.value = it }
        }
    }

    suspend fun closeSession() {
        activeSession?.close()
        activeSession = null
        sessionProtocol = null
        sessionForwardJob?.cancel()
        sessionForwardJob = null
        conversationFlow.value = null
    }

    suspend fun resetConversation() {
        TerminalSessionPool.closeAll()
        SessionRegistry.clear()
        closeSession()
    }

    suspend fun stopCurrentRound() {
        activeSession?.stop()
    }

    private suspend fun openOkiaWithDefaultProtocol(
        protocol: LlmProtocol,
        restore: SessionSnapshot?,
        config: ResolvedLlmConfig,
    ): Okia {
        val endpoint = config.endpoint.ifBlank { protocolDefaultEndpointFallback(protocol) }
        val wireProtocol = wireProtocolFor(protocol)
        val saver = ensureImageSaver()
        return Okia.open(wireProtocol, restore) {
            this.endpoint = endpoint
            apiKey = config.apiKey
            model = config.model
            hooks += killToolResourcesHook
            hooks += fixIncompleteToolCallsHook
            hooks += toolResultBudgetHook
            hooks += compactPruningHook
            hooks += requestSizeLogHook
            idleTimeoutSeconds = config.idleTimeoutSeconds ?: AgentSessionEngine.NO_IDLE_TIMEOUT_SECONDS
            retryPolicy = RetryPolicy(maxAttempts = config.retryMaxAttempts)
            maxTokens = config.maxTokens
            toolRegistry = this@OkiaSessionAssembler.toolRegistry
            imageLoader = this@OkiaSessionAssembler.imageLoader
            imageSaver = saver
            supportsImages = imageLoader != null && config.supportsImages
            thinkingLevel = config.thinkingLevel
            proxy = config.proxy
            requestLogger = androidRequestLogger
            repetitionConfig = config.repetitionSensitivity.toRepetitionConfig()
        }
    }

    internal fun isImageSupported(configSupportsImages: Boolean): Boolean {
        return imageLoader != null && configSupportsImages
    }

    internal fun protocolDefaultEndpointFallback(protocol: LlmProtocol): String {
        return when (protocol) {
            LlmProtocol.DeepSeek -> "https://api.deepseek.com/chat/completions"
            LlmProtocol.OpenAiChatCompletions -> "https://api.openai.com/v1/chat/completions"
            LlmProtocol.GoogleOpenAi ->
                "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

            LlmProtocol.OpenAiResponses -> "https://api.openai.com/v1/responses"
            LlmProtocol.AnthropicMessages -> "https://api.anthropic.com/v1/messages"
        }
    }

    private fun wireProtocolFor(protocol: LlmProtocol): ChatProtocol = when (protocol) {
        LlmProtocol.DeepSeek -> OpenAIChatCompletionProtocol()
        LlmProtocol.OpenAiChatCompletions ->
            OpenAIChatCompletionProtocol(Json, OpenAIChatCompletionCompat())

        LlmProtocol.GoogleOpenAi ->
            OpenAIChatCompletionProtocol(Json, GoogleOpenAiCompat())

        LlmProtocol.OpenAiResponses -> OpenAIResponsesProtocol()
        LlmProtocol.AnthropicMessages -> AnthropicMessagesProtocol()
    }

    suspend fun generateSummary(
        protocol: LlmProtocol,
        config: ResolvedLlmConfig,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val endpoint = config.endpoint.ifBlank { protocolDefaultEndpointFallback(protocol) }
        val wireProtocol = wireProtocolFor(protocol)
        val session = Okia.open(wireProtocol) {
            this.endpoint = endpoint
            apiKey = config.apiKey
            model = config.model
            idleTimeoutSeconds = config.idleTimeoutSeconds ?: AgentSessionEngine.NO_IDLE_TIMEOUT_SECONDS
            retryPolicy = RetryPolicy(maxAttempts = config.retryMaxAttempts)
            maxTokens = config.maxTokens
            thinkingLevel = config.thinkingLevel
            proxy = config.proxy
            requestLogger = androidRequestLogger
            repetitionConfig = config.repetitionSensitivity.toRepetitionConfig()
        }
        try {
            var summary = ""
            session.send(
                text = userPrompt,
                options = TurnOptions(systemPrompt = systemPrompt),
            ) { event ->
                if (event is TurnEvent.TurnCompleted) {
                    summary = event.message.content.filterIsInstance<ContentBlock.Text>()
                        .joinToString("\n") { it.text }
                }
            }
            return summary
        } finally {
            session.close()
        }
    }

    fun validateLlmConfig(config: RuntimeLlmConfig) {
        if (config.endpoint.isBlank() || config.model.isBlank()) {
            throw LlmConfigRequiredException()
        }
    }

    private val fixIncompleteToolCallsHook = object : Hooks {
        override suspend fun beforeSerialization(request: SerializationHolder) {
            val history = request.history
            val missing = countMissingToolResults(history)
            if (missing > 0) {
                Logger.i(LOG_TAG, "fixIncompleteToolCalls history=${history.size} missing=$missing")
            }
            val fixed = withMissingToolResultsFilled(history)
            if (fixed != history) {
                request.write(request.snapshot, fixed, "fix_incomplete_tool_calls")
                Logger.i(
                    LOG_TAG,
                    "fixIncompleteToolCalls patched ${fixed.size - history.size} results"
                )
            }
        }
    }

    private fun countMissingToolResults(history: List<Message>): Int {
        val answered =
            history.filterIsInstance<Message.ToolResult>().mapTo(mutableSetOf()) { it.callId }
        var missing = 0
        for (message in history) {
            if (message !is Message.Assistant) continue
            missing += message.message.content
                .filterIsInstance<ContentBlock.ToolCall>()
                .count { it.id !in answered }
        }
        return missing
    }

    private fun withMissingToolResultsFilled(history: List<Message>): List<Message> {
        val answered =
            history.filterIsInstance<Message.ToolResult>().mapTo(mutableSetOf()) { it.callId }
        if (answered.isEmpty() && history.none { it is Message.Assistant }) return history
        val patched = mutableListOf<Message>()
        var changed = false
        for (message in history) {
            patched += message
            if (message !is Message.Assistant) continue
            val missing = message.message.content
                .filterIsInstance<ContentBlock.ToolCall>()
                .filter { it.id !in answered }
            if (missing.isEmpty()) continue
            changed = true
            missing.forEach { call ->
                patched += Message.ToolResult(
                    callId = call.id,
                    toolName = call.name,
                    outcome = ToolCallOutcome.Failure(
                        message = TOOL_RESULT_MISSING,
                        content = TOOL_RESULT_MISSING,
                    ),
                )
            }
        }
        return if (changed) patched else history
    }

    private val killToolResourcesHook = object : Hooks {
        override suspend fun beforeStop(calls: List<ContentBlock.ToolCall>) {
            Logger.i(LOG_TAG, "beforeStop killing tool resources dispatchedCalls=${calls.size}")
            TerminalSessionPool.closeAll()
            SessionRegistry.clear()
        }
    }
}
