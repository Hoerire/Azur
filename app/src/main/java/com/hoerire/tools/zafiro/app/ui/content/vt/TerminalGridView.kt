package com.hoerire.tools.zafiro.app.ui.content.vt

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp

private val VtFgArgb = 0xFFE5E5E5.toInt()
private val VtBgArgb = 0xFF000000.toInt()
private val VtFg = Color(VtFgArgb)

/** 终端块内的竖向滚动全吃掉：内部滚到底 / 顶也不把拖动透给整条对话。 */
private val VtScrollIsolation: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available.copy(x = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = 0f, y = available.y)
}

/**
 * 把 [screen] 画成带颜色的终端网格。
 *
 * 只有可视行真正成文（LazyColumn），且相邻同色格会合并成一个 span，
 * 所以几百行的输出也不会一帧拼几万个字符。
 */
@Composable
fun TerminalGridView(
    screen: VtScreen,
    modifier: Modifier = Modifier,
    cursorVisible: Boolean = false,
    fontSize: TextUnit = 11.sp,
    lineHeight: TextUnit = 13.sp,
    /**
     * true = 超宽的行**软换行**成多行显示（全屏终端页用）：一格不再等于一行，
     * 但长命令/长日志不会被 `…` 截掉看不见；false = 一格一行、超出截断（块内小窗用）。
     */
    wrapLines: Boolean = false,
) {
    val version = screen.version
    val count = remember(version) { screen.lineCount }
    val listState = rememberLazyListState()
    val baseStyle = MaterialTheme.typography.bodySmall
    val textStyle = remember(baseStyle, fontSize, lineHeight) {
        baseStyle.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = 0.sp,
            // 关掉 Android 默认的字体上下留白，否则一行会被撑到 ~2 倍行高
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
    // 跟随底部：默认贴底；只要用户手动拖过就不再抢，滚回底部才恢复
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { dragging ->
            if (dragging) {
                follow = false
            } else {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                follow = info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
            }
        }
    }
    LaunchedEffect(version, follow) {
        if (!follow || count <= 0) return@LaunchedEffect
        runCatching { listState.scrollToItem(count - 1) }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.nestedScroll(VtScrollIsolation),
    ) {
        // key 用「绝对行号」：超出 maxLines 裁掉头部行时，保留行的 key 不变，翻阅时不会跳
        items(count = count, key = { it + screen.baseIndex }) { index ->
            TerminalGridLine(
                screen = screen,
                index = index,
                version = version,
                cursorVisible = cursorVisible,
                textStyle = textStyle,
                wrapLines = wrapLines,
            )
        }
    }
}

/**
 * 单行渲染。[version] 作为参数显式下传：LazyColumn 的 item 会跳过「参数没变」的重组，
 * 只靠 item 内部读状态在部分版本上不会刷新，这里用参数变化保证一定重画。
 */
@Composable
private fun TerminalGridLine(
    screen: VtScreen,
    index: Int,
    version: Int,
    cursorVisible: Boolean,
    textStyle: TextStyle,
    wrapLines: Boolean,
) {
    val line = remember(version, index, cursorVisible) {
        val cells = screen.lineAt(index)
        val cursor = if (cursorVisible && index == screen.cursorRow) screen.cursorCol else -1
        if (isSentinelLine(cells)) AnnotatedString("") else buildLine(cells, cursor)
    }
    if (line.isNotEmpty()) {
        Text(
            text = line,
            style = textStyle,
            color = VtFg,
            modifier = Modifier.fillMaxWidth(),
            // 块内小窗：一格 = 一个屏幕行，超宽就截断（否则 80 列进度条会被挤成 3 行、网格错位）。
            // 全屏页：wrapLines=true，按屏幕宽度换行，长命令 / 长日志都能看全。
            softWrap = wrapLines,
            maxLines = if (wrapLines) Int.MAX_VALUE else 1,
            overflow = if (wrapLines) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

/** 把一行单元格拼成 AnnotatedString；相同样式的连续格合并成一个 span。 */
private fun buildLine(cells: List<VtCell>, cursorCol: Int): AnnotatedString = buildAnnotatedString {
    var end = cells.size
    while (end > 0) {
        val cell = cells[end - 1]
        if (cell.ch == ' ' && cell.bg == VT_DEFAULT_COLOR) end-- else break
    }
    if (end == 0) {
        append(' ')
        return@buildAnnotatedString
    }
    var runStart = 0
    var runCell: VtCell? = null
    var runCursor = false
    var i = 0
    while (i < end) {
        val cell = cells[i]
        if (cell.ch == '\u0000') {
            i++
            continue
        }
        val cursor = i == cursorCol
        val open = runCell
        if (open == null) {
            runCell = cell
            runStart = length
            runCursor = cursor
        } else if (cursor != runCursor || !sameStyle(open, cell)) {
            addStyle(spanFor(open, runCursor), runStart, length)
            runCell = cell
            runStart = length
            runCursor = cursor
        }
        append(cell.ch)
        i++
    }
    runCell?.let { addStyle(spanFor(it, runCursor), runStart, length) }
    if (length == 0) append(' ')
}

private fun sameStyle(a: VtCell, b: VtCell): Boolean =
    a.fg == b.fg && a.bg == b.bg && a.bold == b.bold && a.underline == b.underline && a.inverse == b.inverse

private fun spanFor(cell: VtCell, cursor: Boolean): SpanStyle {
    var fg = if (cell.fg == VT_DEFAULT_COLOR) VtFgArgb else cell.fg
    var bg = if (cell.bg == VT_DEFAULT_COLOR) VtBgArgb else cell.bg
    var paintBg = cell.bg != VT_DEFAULT_COLOR || cell.inverse
    if (cell.inverse) {
        val swap = fg
        fg = bg
        bg = swap
    }
    if (cursor) {
        fg = VtBgArgb
        bg = VtFgArgb
        paintBg = true
    }
    return SpanStyle(
        color = Color(fg),
        background = if (paintBg) Color(bg) else Color.Unspecified,
        fontWeight = if (cell.bold) FontWeight.Bold else null,
        textDecoration = if (cell.underline) TextDecoration.Underline else null,
    )
}

/** 工具自己往命令尾部追加的完成哨兵（`__SESSION_EXIT_xxx:0__`），不画出来。 */
private fun isSentinelLine(cells: List<VtCell>): Boolean {
    if (cells.isEmpty() || cells[0].ch != '_') return false
    val head = StringBuilder(24)
    var i = 0
    while (i < cells.size && head.length < 24) {
        val ch = cells[i].ch
        if (ch != '\u0000') head.append(ch)
        i++
    }
    return head.startsWith("__SESSION_EXIT_")
}
