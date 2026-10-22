package com.hoerire.tools.zafiro.chat.agentic

import com.hoerire.tools.logging.Logger
import com.hoerire.tools.okia.message.ToolCallOutcome
import com.hoerire.tools.okia.tooling.ToolCallContext
import com.hoerire.tools.okia.tooling.ToolExecutor
import com.hoerire.tools.zafiro.api.AgentControl
import com.hoerire.tools.zafiro.chat.LocalTool
import com.hoerire.tools.zafiro.chat.ResolvedTools
import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinToolExecutor
import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinToolResult
import com.hoerire.tools.zafiro.chat.agentic.buildin.TextToolResult
import com.hoerire.tools.zafiro.chat.agentic.buildin.TextToolResultCodec
import com.hoerire.tools.zafiro.chat.agentic.stream.LocalToolResultClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * OKIA ToolExecutor 适配：把 Azur 本地工具（内置工具 + 兼容层遗留项）的执行接到
 * OKIA 工具循环。执行永不抛异常，总是产出 ToolCallOutcome（§5.5 契约）：
 * - 结果 JSON（BuiltinToolResult 输出）按 "ok" 字段拆解
 *   Success / Failure；文本协议结果（TextResultBuiltinTool）经
 *   TextToolResultCodec 拆解
 * - onInterrupt：本地工具未被框架调用（okia §8.18 Q1），实现为 Interrupted
 * - 内置 Python 工具链已整体移除（改由宿主 hosttools 的 CPython 3.12 承担），
 *   `LocalTool.Py` 类型仅为老设置 JSON 反序列化兼容而保留，不再注入执行
 * Design source: okia ToolExecutor 契约。
 */
class LocalToolExecutor(
    private val builtinToolExecutor: BuiltinToolExecutor = BuiltinToolExecutor(),
    private val currentTools: () -> ResolvedTools?,
    private val tenantId: String = "",
    private val agentControlProvider: (() -> AgentControl?)? = null,
) : ToolExecutor {

    private companion object {
        const val LOG_TAG = "zafiro_LocalToolExecutor"
    }

    override suspend fun execute(call: ToolCallContext): ToolCallOutcome {
        val control = agentControlProvider?.invoke()
        val context = if (tenantId.isNotBlank() || control != null) {
            AgentTenantContext(tenantId = tenantId, agentControl = control)
        } else {
            null
        }
        val raw = if (context != null) {
            kotlinx.coroutines.withContext(context) {
                executeLocal(name = call.name, argumentsJson = call.argumentsJson)
            }
        } else {
            executeLocal(name = call.name, argumentsJson = call.argumentsJson)
        }
        return decodeOutcome(raw)
    }

    // TODO: 如果工具执行过程中已有部分流式输出，中断时应保留并拼接部分输出（如 "$partial\n\n[Tool execution was interrupted by user.]"）
    override fun onInterrupt(call: ToolCallContext): ToolCallOutcome =
        ToolCallOutcome.Interrupted(content = "Tool execution was interrupted by user.")

    // ── 本地执行（builtin / py 路由）──────────────────────────────────

    private suspend fun executeLocal(name: String, argumentsJson: String): String {
        val startedAtMs = System.currentTimeMillis()
        Logger.i(
            LOG_TAG,
            "local tool start name=$name argsLength=${argumentsJson.length}"
        )
        val tools = currentTools()
        val builtinTool = tools
            ?.builtinTools
            .orEmpty()
            .filterIsInstance<LocalTool.Builtin>()
            .firstOrNull { it.name == name }
        if (builtinTool != null) {
            return builtinToolExecutor.execute(
                tool = builtinTool.tool,
                argumentsJson = argumentsJson,
            ).also { result ->
                Logger.i(
                    LOG_TAG,
                    "local tool done name=$name kind=builtin resultLength=${result.length} " +
                            "elapsedMs=${System.currentTimeMillis() - startedAtMs}"
                )
            }
        }

        Logger.w(
            LOG_TAG,
            "local tool not executable name=$name " +
                    "elapsedMs=${System.currentTimeMillis() - startedAtMs}"
        )
        return BuiltinToolResult.failure(
            code = "LOCAL_TOOL_NOT_EXECUTABLE",
            message = "Local tool '$name' is not executable in current runtime.",
            hint = "Check builtin_tool_flags configuration.",
        ).toJsonString()
    }

    // ── 结果 → ToolCallOutcome 拆解 ────────────────────────────────────────

    private fun decodeOutcome(raw: String): ToolCallOutcome {
        val json = try {
            Json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }
        if (json != null) {
            // JSON 结构化错误判定复用统一解码器（error.code / ok=false / 非零
            // exit_code，覆盖 Hermes 风格 terminal 结果）；无错误标记 = 成功
            val failure = LocalToolResultClassifier.failureMessage(raw)
            return if (failure != null) {
                ToolCallOutcome.Failure(message = failure, content = raw)
            } else {
                // 检查工具结果是否包含图片引用（如 view_image）
                val images = extractImagesFromResult(json)
                ToolCallOutcome.Success(content = raw, images = images)
            }
        }
        // 非 JSON：文本协议工具结果（#!tool-result 头）
        val text = TextToolResultCodec.decode(raw)
        if (text != null) {
            return when (text.status) {
                TextToolResult.Status.Success -> ToolCallOutcome.Success(content = raw)
                TextToolResult.Status.Failure -> ToolCallOutcome.Failure(
                    message = text.message ?: text.code ?: "Tool failed.",
                    content = raw,
                )
            }
        }
        // 未知格式：保守成功，原文回喂模型（沿用旧运行时行为）
        return ToolCallOutcome.Success(content = raw)
    }

    // ── 图片引用提取 ──────────────────────────────────────────────────────────

    /** 从 JSON 结果中提取图片引用（如果工具返回了 image 字段）。 */
    /** 从 JSON 结果中提取图片引用列表（如果工具返回了 image 字段）。 */
    private fun extractImagesFromResult(json: JsonObject?): List<com.hoerire.tools.okia.message.ContentBlock.Image> {
        if (json == null) return emptyList()
        // 支持 {"image": {"path": ..., "mime_type": ...}} 格式
        val imageObj = json["image"] as? JsonObject
            ?: (json["data"] as? JsonObject)?.get("image") as? JsonObject
            ?: return emptyList()
        val path = (imageObj["path"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?: return emptyList()
        val mimeType =
            (imageObj["mime_type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?: "image/jpeg"
        return listOf(com.hoerire.tools.okia.message.ContentBlock.Image(path, mimeType))
    }
}
