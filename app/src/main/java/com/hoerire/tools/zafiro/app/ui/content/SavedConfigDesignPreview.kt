package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ══════════════════════════════════════════════════════════════════════
// 设计稿 preview（仅预览，方案敲定后拆进正式实现）：
//   1. ModelConfigRedesignPreview      —— 模型配置页改版全貌
//        Access 块（Endpoint / Model / Protocol★ / API Key）
//        + Saved Configuration 列表块（激活态 ◉、滑动删除）
//        + System Prompt 全局块（从配置表单中拆出）
//   2. GeneralSettingsRedesignPreview  —— 设置页新增分组（About 同款样式）
//        Language Value Row★ / 启动时载入上次对话 toggle（默认关）
//   3. SingleChoiceDialogPreview       —— 协议/语言共用选择弹窗
//        面板内部自带 verticalScroll + heightIn 封顶（超长列表内部滚动，
//        弹窗不会撑破），按钮只保留一个「取消」
// 右上角入口：模型配置页 Refresh → Add（由 PageChrome rightAction 承担，
// preview 里不渲染顶栏）。
// ══════════════════════════════════════════════════════════════════════

private val MOCK_PROTOCOLS = listOf(
    "deepseek",
    "openai-chat-completions",
    "google-openai",
    "openai-responses",
    "anthropic-messages",
)

