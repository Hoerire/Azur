package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

// ── 多工具链头部图标候选 ─────────────────────────────────────────────────────

private data class IconCandidate(val icon: ImageVector, val name: String, val note: String)

private val MultiToolCandidates = listOf(
    IconCandidate(Icons.Filled.Build, "Build（扳手）", "当前默认 · 对照组"),
    IconCandidate(Icons.Filled.Hub, "Hub", "中心节点连多条线，有『并行派发一群』感"),
    IconCandidate(Icons.Filled.Widgets, "Widgets", "拼块组合，表达批量执行"),
    IconCandidate(Icons.Filled.Layers, "Layers", "堆叠层，表达一串/一组"),
    IconCandidate(Icons.Filled.AccountTree, "AccountTree", "调用链分叉树，表达工具链"),
    IconCandidate(Icons.AutoMirrored.Filled.ViewList, "ViewList", "清单，最直白的『一批』"),
)

// ── Loading / Running 状态 ───────────────────────────────────────────────────

