package com.hoerire.tools.zafiro.app.ui.content

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.hoerire.tools.uikit.infra.shape.G2FieldShape
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.model.ToolPresentation
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatImage
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatBlock
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatTurn
import com.hoerire.tools.zafiro.app.ui.model.home.HomeToolState
import com.hoerire.tools.zafiro.app.ui.model.home.HomeToolStatus
import com.hoerire.tools.zafiro.app.ui.content.vt.TerminalGridView
import com.hoerire.tools.zafiro.app.ui.content.vt.VtScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── shared animation specs ─────────────────────────────────────────────────

private val StaggerFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = 300f)
private val StaggerSlideSpring =
    spring<IntOffset>(dampingRatio = 0.8f, stiffness = 300f)

// ── nested scroll: pass through user drag, block fling inertia ─────────────

private val BlockFlingScrollPropagation: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = when {
        source == NestedScrollSource.Fling -> available.copy(x = 0f)
        else -> Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = 0f, y = available.y)
}

// ── ToolChain — stateless, state driven by ViewModel ────────────────────

/** 命令型工具（精确匹配）：结果体为「命令单行 + 输出」上下分段样式。 */
private val CommandToolNames = setOf("shell", "ssh")

/**
 * 真正有 PTY 会话的工具（精确匹配）：只有它们才挂实时终端采集。
 *
 * 仅有 PTY 会话的工具才挂实时终端采集；跑在独立进程里的工具不登记会话，
 * 若一起 preload 会让「终端」页停在脚本原文上、屏永远是空的。
 */
private val TerminalToolNames = setOf("shell", "ssh")

/**
 * 聊天页根组合调用：把当前所有 RUNNING 的命令型工具**提前**挂上采集。
 *
 * 块本身在 LazyColumn 里按需组合，等它滚进视野才 attach 会晚订阅 3~5 秒（交接文档 §9.6）。
 * 这一层挂在列表容器上，turn 再远也照常组合，所以工具调用一开始就能把原始字节收下来，
 * 不需要块已经出现在屏幕上。
 */
@Composable
internal fun preloadLiveTerminals(turns: List<HomeChatTurn>) {
    // 扫全量 turn：块要滚进视野才组合，等它组合再订阅会晚 3~5 秒（§9.6），这里提前挂上。
    // 只收集「参数已经到齐」的调用：hint 为空时只能靠「当前只有一个运行中会话」兜底匹配，
    // 一轮里并行两条 shell 会把 B 块挂到 A 的会话上。参数到达时这里会重跑。
    val running = remember(turns) {
        turns.flatMap { turn ->
            turn.blocks.mapNotNull { block ->
                val status = (block as? HomeChatBlock.Tool)?.status ?: return@mapNotNull null
                if (status.state != HomeToolState.Running) return@mapNotNull null
                if (status.name !in TerminalToolNames) return@mapNotNull null
                val key = status.callId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val hint = commandOf(status.inputText)
                    .ifEmpty { ToolPresentation.previewOf(status.inputText).orEmpty() }
                if (hint.isEmpty()) return@mapNotNull null
                key to hint
            }
        }
    }
    LaunchedEffect(running) {
        running.forEach { (key, hint) -> ToolTerminals.attach(key, hint) }
    }
}


/** 结果文本型工具（精确匹配）：结果体为等宽结果文本（可选中、无复制按钮）。 */
private val ResultTextToolNames = setOf("load_skill")

/**
 * Stateless tool call list. Single-tool: renders one [CollapsibleBlock] directly.
 * Multi-tool: renders a header [CollapsibleBlock] (title = count) whose expansion
 * reveals a staggered list of per-tool [CollapsibleBlock] rows.
 * 图标按工具名分派（ToolPresentation.forTool），无专有布局时走默认折叠块。
 */
@Composable
fun ToolChain(
    tools: List<HomeToolStatus>,
    isExpanded: Boolean,
    expandedResults: Set<Int>,
    onToggleRun: () -> Unit,
    onToggleResult: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 展开内容（工具结果）点击回调；null 时不拦截点击（head 仍只管展开/收起）。 */
    onContentClick: (() -> Unit)? = null,
) {
    val anyRunning = tools.any { it.state == HomeToolState.Running }
    // 运行中挂计时：多工具串联时用户只看到转圈，不知道跑了多久
    val runningKey = tools.firstOrNull { it.state == HomeToolState.Running }?.callId
    val runningSecs = runningSeconds(runningKey, anyRunning)
    if (tools.size == 1) {
        val status = tools[0]
        SingleToolRow(
            status = status,
            isOpen = 0 in expandedResults,
            onToggle = { onToggleResult(0) },
            onContentClick = onContentClick,
        )
    } else {
        CollapsibleBlock(
            icon = ToolPresentation.Multi,
            title = pluralStringResource(R.plurals.ui_tool_chain_count, tools.size, tools.size) +
                    if (anyRunning) " · 执行中 ${runningSecs}s" else "",
            // 不自动展开：展开会顶动整条会话，用户不需要每条命令都看
            isExpanded = isExpanded,
            onToggle = onToggleRun,
            modifier = modifier,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
                tools.forEachIndexed { index, status ->
                    StaggeredEntry(index = index, staggerMs = index * 40L) {
                        SingleToolRow(
                            status = status,
                            isOpen = index in expandedResults,
                            onToggle = { onToggleResult(index) },
                            onContentClick = onContentClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 运行中耗时秒数：每 250ms 刷新，工具结束或 callId 变化自动停。
 * 只做本地计时——不依赖引擎上报进度，任何工具都能立刻显示「跑了多久」。
 */
@Composable
private fun runningSeconds(key: Any?, running: Boolean): Long {
    val secs by produceState(0L, key, running) {
        if (!running) return@produceState
        val start = System.currentTimeMillis()
        while (true) {
            value = (System.currentTimeMillis() - start) / 1000L
            kotlinx.coroutines.delay(250)
        }
    }
    return secs
}

@Composable
private fun SingleToolRow(
    status: HomeToolStatus,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onContentClick: (() -> Unit)? = null,
) {
    val hasResult = status.resultText != null || status.failedReason != null
    val isRunning = status.state == HomeToolState.Running
    var stopNote by remember(status.callId) { mutableStateOf<String?>(null) }
    // 命令型工具的输入预览已在结果体上半区展示，标题不再重复拼预览
    val useCommandBody = status.name in CommandToolNames && !status.inputText.isNullOrBlank()
    // 命令型工具也允许展开：shell/ssh 走 PTY 帧流。
    // 默认仍是收起（不自动展开，避免顶动会话），要看就点开。
    val inlineLive = status.name in CommandToolNames
    val expandableRow = true
    val inputPreview = ToolPresentation.previewOf(status.inputText)
    // 命令原文只算一次：标题预览之外，实时终端订阅也靠它匹配会话
    val commandHint = remember(status.inputText, inputPreview) {
        commandOf(status.inputText).ifEmpty { inputPreview.orEmpty() }
    }
    val runningSecs = runningSeconds(status.callId, isRunning)
    val title = buildString {
        append(status.displayNameRes?.let { stringResource(it) } ?: status.name)
        if (!useCommandBody) {
            inputPreview?.let { preview -> append(" · ").append(preview) }
        }
        if (isRunning) append(" · 执行中 ${runningSecs}s")
    }
    CollapsibleBlock(
        icon = ToolPresentation.forTool(status.name),
        title = title,
        isExpanded = isOpen && expandableRow,
        isRunning = isRunning,
        expandable = expandableRow,
        onStop = {
            stopNote = "^C  当前会话已被手动停止"
            ToolLiveSession.interruptAsync(commandHint)
        },
        onToggle = onToggle,
    ) {
        val contentModifier = if (onContentClick != null) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onContentClick!!,
            )
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(contentModifier)
        ) {
            when {
                status.images.isNotEmpty() -> ImagePreviewBody(images = status.images)
                useCommandBody -> CodeToolBody(
                    command = inputPreview.orEmpty(),
                    copyText = status.inputText,
                    output = if (isRunning) {
                        // 无输出也要让人知道「还在跑、跑多久」——不依赖模型是否打印进度
                        liveCommandOutput(isRunning, commandHint)
                            .ifBlank { "… 已运行 ${runningSecs}s，暂无输出" }
                    } else {
                        displayOutput(status).trim()
                    },
                    isError = status.state == HomeToolState.Failed,
                    running = isRunning,
                    note = stopNote,
                    terminal = liveTerminal(running = isRunning, hint = commandHint, key = status.callId.orEmpty()),
                )

                status.name in ResultTextToolNames && status.state != HomeToolState.Failed ->
                    ResultTextBody(text = displayOutput(status).trim())

                else ->
                    // 兕底：正文只显示本地化「成功 / 失败」，失败红色。
                    FallbackResultBody(isFailed = status.state == HomeToolState.Failed)
            }
        }
    }
}

// ── tool result bodies ──────────────────────────────────────────────────────

/**
 * 图片预览体：工具返回了图片（view_image / screenshot 等）时展示落盘图缩略卡。
 * 通用分派（images 非空即渲染），不依赖工具名；复用消息图片卡组件。
 */
@Composable
private fun ImagePreviewBody(images: List<HomeChatImage>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BlockSpacing, Alignment.CenterHorizontally),
    ) {
        images.forEach { image ->
            HomeChatImageCard(
                image = image,
                size = ToolImagePreviewSize,
                cornerRadius = 18.dp,
                onRemove = null,
            )
        }
    }
}

/**
 * 命令型结果体：上下两段独立着色，中间 2dp 透明缝隙露出页面背景（M3E 分割样式）。
 * 上段：命令单行（bodyMedium）+ 复制按钮；下段：输出最多 6 行（bodySmall 等宽）
 * + 复制按钮；[isError] 时仅下段变红。分割侧两角 2dp 小圆角，外侧两角 16dp G2。
 */
@Composable
private fun CodeToolBody(
    command: String,
    copyText: String?,
    output: String,
    isError: Boolean,
    running: Boolean,
    note: String? = null,
    terminal: VtScreen? = null,
) {
    val termBg = Color(0xFF12161C)
    val termFg = Color(0xFFD9E2EC)
    val promptFg = Color(0xFF6EE7A8)
    val lines = remember(output) {
        output.trim().lines().filterNot { it.contains("__SESSION_EXIT_") }
    }
    val listState = rememberLazyListState()
    // 有原始字节喂出来的终端屏就用真终端渲染（颜色/光标/进度条都在），否则回落到纯文本
    val grid = terminal?.takeIf { it.lineCount > 0 }
    // 真终端给更高一档：命令头压成一行后，输出能看六七行
    val boxHeight = if (grid != null) 320.dp else 220.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(termBg, G2FieldShape(16.dp, 16.dp, 16.dp, 16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(boxHeight)
                .padding(start = 12.dp, end = 40.dp)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = promptFg)) { append("$ ") }
                    append(command)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = termFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (grid != null) {
                TerminalGridView(
                    screen = grid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 6.dp),
                    cursorVisible = running,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(TerminalScrollIsolation)
                        .padding(top = 6.dp),
                ) {
                    items(lines.size) { index ->
                        Text(
                            text = lines[index],
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (isError) Color(0xFFFF8F8F) else termFg,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                LaunchedEffect(output) {
                    listState.scrollToItem((lines.size - 1).coerceAtLeast(0))
                }
            }
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = promptFg,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        MiniCopyButton(
            text = copyText ?: command,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
        )
    }
}

/** 小号复制按钮（比标准 IconButton 小、无背景直接融入），点击写入剪贴板。 */
@Composable
private fun MiniCopyButton(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .size(CopyBtnSize)
            .clickable {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy Icon",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            modifier = Modifier.size(CopyIconSize),
        )
    }
}

/** 兜底结果体：UI 与 Thinking 同款，正文只显示本地化「成功 / 失败」，失败红色。 */
@Composable
private fun FallbackResultBody(isFailed: Boolean) {
    Text(
        text = stringResource(if (isFailed) R.string.ui_tool_status_failed else R.string.ui_tool_status_success),
        style = MaterialTheme.typography.bodySmall,
        color = if (isFailed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/**
 * 结果文本体：面板底色 + 等宽结果文本（如 load_skill 返回的 SKILL.md 内容，
 * 超限截断时尾部含绝对路径提示）。复用 ToolResultText（已包 SelectionContainer，
 * 文本可选中）；不带复制按钮。
 */
@Composable
private fun ResultTextBody(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = CommandPanelPadX)
            .padding(vertical = OutputPadY),
    ) {
        ToolResultText(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 提取展示用输出文本：
 * - 「#!tool-result」信封（文本协议类工具）→ 取空行后的 payload；
 * - Hermes 扁平 JSON（terminal）→ 拼接 stdout/output/stderr 非空项；
 * - 其余原文。
 */
private fun displayOutput(status: HomeToolStatus): String {
    val raw = status.resultText ?: return status.failedReason.orEmpty()
    if (raw.startsWith("#!tool-result")) {
        val sep = raw.indexOf("\n\n")
        return if (sep != -1) raw.substring(sep + 2) else raw
    }
    val json = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return raw
    val parts = listOf("stdout", "output", "stderr")
        .mapNotNull { key -> json[key]?.jsonPrimitive?.contentOrNull }
        .filter { it.isNotBlank() }
    return if (parts.isEmpty()) raw else parts.joinToString("\n")
}

// ── staggered entry ─────────────────────────────────────────────────────────

@Composable
private fun StaggeredEntry(
    index: Int,
    staggerMs: Long,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(staggerMs)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(StaggerFadeSpring) + slideInVertically(StaggerSlideSpring) { it / 4 },
        exit = fadeOut(tween(80)),
    ) { content() }
}

// ── result text — 共享有界滚动文本（工具结果 / 思考展开共用，高度上限统一 102dp） ───

@Composable
internal fun ToolResultText(
    text: String,
    style: TextStyle,
    color: Color,
    /** active 思考块流式更新时自动滚到底跟随；用户手动拖动后暂停，滚回底部后恢复。 */
    autoScrollToEnd: Boolean = false,
) {
    var overflow by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var shouldFollow by rememberScrollFollowState(
        interactionSource = scrollState.interactionSource,
        isScrollInProgress = { scrollState.isScrollInProgress },
        isAtEnd = { !scrollState.canScrollForward },
    )

    // maxValue 更新（内容增长/布局完成）即滚到底；autoScrollToEnd 置位瞬间先跟随当前尾部。
    LaunchedEffect(autoScrollToEnd, shouldFollow, scrollState.maxValue) {
        if (autoScrollToEnd && shouldFollow) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ResultScrollMaxHeight)
            .let {
                if (overflow) {
                    it
                        .nestedScroll(BlockFlingScrollPropagation)
                        .verticalScroll(scrollState)
                } else {
                    it
                }
            },
        contentAlignment = Alignment.TopStart,
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = if (overflow) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = overflow,
                onTextLayout = { layoutResult ->
                    if (!overflow && layoutResult.hasVisualOverflow) {
                        overflow = true
                    }
                },
            )
        }
    }
}

/** 终端块内的竖向滚动全吃掉：内部滚到底/顶也不把拖动透给整条对话。 */
private val TerminalScrollIsolation: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available.copy(x = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = 0f, y = available.y)
}
