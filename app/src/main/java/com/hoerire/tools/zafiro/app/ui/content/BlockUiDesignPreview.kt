package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 思考块内容：正文区，展开后左右比头部再多一点内缩（兜底样式的参照）。 */
private val ThinkingBody = """
    让我先拆解一下这个问题。用户想要在语音助手里获得一个能执行本地工具的智能回答，
    我需要判断：任务是否允许本地执行、需要调用哪些工具、以及如何把多次工具结果整理成一段连贯的中文回答。
""".trimIndent()

/** 兜底工具结果正文：与 Thinking 同款排版（bodyMedium / onSurface）。 */
private val DefaultToolBody =
    "Retrieved 12 documents. Matched 3 results. Summarized by relevance score."

/** 超长内容（内部文本）：默认展开，验证 ToolResultText 的 102dp 上限与内部滚动。 */
private val LongContentBody = """
    Retrieved 12 documents. Matched 3 results. Summarized by relevance score. Additional context:
    the query matched several internal knowledge base entries with overlapping content, ranked by
    cosine similarity against the embedding index. The top hit was an internal design doc on the
    agent runtime, followed by the Onboarding flow spec and the tool bridge contract.
""".trimIndent()

