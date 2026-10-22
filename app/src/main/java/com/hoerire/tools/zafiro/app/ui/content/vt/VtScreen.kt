package com.hoerire.tools.zafiro.app.ui.content.vt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** 默认前景 / 背景（-1 = 跟随主题）。 */
const val VT_DEFAULT_COLOR: Int = -1

/** 单行最大列数，超出自动折到下一行（渲染层本来也会软换行，这里只为防跑飞）。 */
private const val VT_MAX_COLS = 1024

private const val ST_GROUND = 0
private const val ST_ESC = 1
private const val ST_CSI = 2
private const val ST_OSC = 3
private const val ST_STR = 4
private const val ST_CHARSET = 5

/**
 * 一个终端单元格。[ch] 为 `\u0000` 表示该格是宽字符（CJK）的占位，渲染时跳过。
 */
class VtCell(
    var ch: Char = ' ',
    var fg: Int = VT_DEFAULT_COLOR,
    var bg: Int = VT_DEFAULT_COLOR,
    var bold: Boolean = false,
    var underline: Boolean = false,
    var inverse: Boolean = false,
) {
    fun blank() {
        ch = ' '
        fg = VT_DEFAULT_COLOR
        bg = VT_DEFAULT_COLOR
        bold = false
        underline = false
        inverse = false
    }

    fun copyStyle(from: VtCell) {
        fg = from.fg
        bg = from.bg
        bold = from.bold
        underline = from.underline
        inverse = from.inverse
    }
}

/**
 * 极简 VT 屏幕：吃原始 PTY 字节（UTF-8 增量解码），自己维护光标 / SGR 颜色 / `\r` 覆盖，
 * 交给 `TerminalGridView` 画成带颜色的网格。
 *
 * 只做「行内光标 + 软换行」这一档能力，够显示 shell 输出、`ls --color`、apt/pip 进度条、
 * 彩色 git diff；不实现滚动区与 alternate screen。
 *
 * 线程约定：只在主线程调用 [feed]（`LaunchedEffect` 默认就是 Main）；
 * [version] 是 Compose 状态，每次喂字节后自增，用来触发重组。
 */
class VtScreen(private val maxLines: Int = 400) {

    private val lines = ArrayList<MutableList<VtCell>>(128)
    private val csi = StringBuilder(24)
    private val pen = VtCell()
    private var carry = ByteArray(0)
    private var state = ST_GROUND

    var version by mutableIntStateOf(0)
        private set

    var cursorRow: Int = 0
        private set

    var cursorCol: Int = 0
        private set

    val lineCount: Int get() = lines.size

    /**
     * 已经被裁掉的头部行数。渲染层把 LazyColumn 的 key 做成 `baseIndex + index`，
     * 这样超出 [maxLines] 裁掉旧行时，保留下来的行 key 不变 —— 否则用户往上翻阅时
     * 每来一行新输出，整屏内容就被顶得乱跳。
     */
    var baseIndex: Int = 0
        private set

    fun lineAt(index: Int): List<VtCell> = if (index in lines.indices) lines[index] else emptyList()

    /** 追加一段原始 PTY 字节。被块边界切断的多字节字符由 carry 拼回去，不会出乱码。 */
    fun feed(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val text = decode(bytes)
        if (text.isEmpty()) return
        parse(text)
        version++
    }

    /** 调试：把最后 [n] 行拼成一行，空格显示成 ·，空行显示成 <E>。 */
    fun debugDump(n: Int): String {
        val from = (lines.size - n).coerceAtLeast(0)
        val sb = StringBuilder()
        for (i in from until lines.size) {
            if (sb.isNotEmpty()) sb.append('|')
            val row = lines[i]
            if (row.isEmpty()) {
                sb.append("<E>")
                continue
            }
            for (cell in row) {
                val ch = cell.ch
                sb.append(if (ch == ' ') '·' else if (ch == '\u0000') '·' else ch)
            }
        }
        return sb.toString()
    }

    fun reset() {
        lines.clear()
        baseIndex = 0
        csi.setLength(0)
        pen.blank()
        carry = ByteArray(0)
        state = ST_GROUND
        cursorRow = 0
        cursorCol = 0
        version++
    }

    // ── 解码 ───────────────────────────────────────────────────────────────

    private fun decode(bytes: ByteArray): String {
        val buf = ByteBuffer.allocate(carry.size + bytes.size)
        buf.put(carry)
        buf.put(bytes)
        buf.flip()
        val out = CharBuffer.allocate(buf.remaining() + 8)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        try {
            decoder.decode(buf, out, false)
        } catch (_: Exception) {
            buf.position(buf.limit())
        }
        carry = if (buf.hasRemaining()) {
            ByteArray(buf.remaining()).also { buf.get(it) }
        } else {
            ByteArray(0)
        }
        out.flip()
        return if (out.hasRemaining()) out.toString() else ""
    }

    // ── 解析 ───────────────────────────────────────────────────────────────

    private fun parse(text: CharSequence) {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when (state) {
                ST_GROUND -> when {
                    c == '\u001B' -> state = ST_ESC
                    c == '\r' -> cursorCol = 0
                    c == '\n' -> {
                        // Unix 的 \n 在真终端里会被 tty 驱动转成 CRLF（ONLCR）；
                        // 我们的 PTY 没做这一步，不补的话纯 \n 输出会逐行右移（阶梯）。
                        cursorCol = 0
                        newline()
                    }
                    c == '\b' -> if (cursorCol > 0) cursorCol--
                    c == '\t' -> cursorCol = (((cursorCol / 8) + 1) * 8).coerceAtMost(VT_MAX_COLS - 1)
                    c.code < 0x20 -> Unit
                    c.code == 0x7F -> Unit
                    else -> putChar(c)
                }

                ST_ESC -> {
                    state = ST_GROUND
                    when (c) {
                        '[' -> {
                            csi.setLength(0)
                            state = ST_CSI
                        }
                        ']' -> state = ST_OSC
                        'P', 'X', '^', '_' -> state = ST_STR
                        '(', ')', '*', '+' -> state = ST_CHARSET
                        else -> Unit
                    }
                }

                ST_CSI -> {
                    val code = c.code
                    if (code in 0x40..0x7E) {
                        handleCsi(c)
                        state = ST_GROUND
                    } else if (csi.length < 64) {
                        csi.append(c)
                    }
                }

                ST_OSC -> when (c) {
                    '\u0007' -> state = ST_GROUND
                    '\u001B' -> state = ST_STR
                }

                ST_STR -> if (c == '\\') state = ST_GROUND

                ST_CHARSET -> state = ST_GROUND
            }
            i++
        }
    }

    private fun handleCsi(final: Char) {
        val raw = csi.toString()
        csi.setLength(0)
        val args = parseParams(raw)
        fun arg(index: Int, fallback: Int): Int = args.getOrNull(index)?.takeIf { it > 0 } ?: fallback
        when (final) {
            'm' -> applySgr(args)
            'H', 'f' -> moveTo(arg(0, 1) - 1, arg(1, 1) - 1)
            'A' -> cursorRow = (cursorRow - arg(0, 1)).coerceAtLeast(0)
            'B' -> moveTo(cursorRow + arg(0, 1), cursorCol)
            'C' -> cursorCol = (cursorCol + arg(0, 1)).coerceAtMost(VT_MAX_COLS - 1)
            'D' -> cursorCol = (cursorCol - arg(0, 1)).coerceAtLeast(0)
            'E' -> moveTo(cursorRow + arg(0, 1), 0)
            'F' -> moveTo(cursorRow - arg(0, 1), 0)
            'G' -> cursorCol = (arg(0, 1) - 1).coerceIn(0, VT_MAX_COLS - 1)
            'd' -> moveTo(arg(0, 1) - 1, cursorCol)
            'J' -> eraseDisplay(args.firstOrNull() ?: 0)
            'K' -> eraseLine(args.firstOrNull() ?: 0)
            else -> Unit
        }
    }

    private fun parseParams(raw: String): IntArray {
        var start = 0
        while (start < raw.length && raw[start] !in '0'..'9' && raw[start] != ';') start++
        if (start >= raw.length) return IntArray(0)
        val parts = raw.substring(start).split(';')
        val out = IntArray(parts.size)
        for (i in parts.indices) out[i] = parts[i].toIntOrNull() ?: 0
        return out
    }

    // ── SGR ────────────────────────────────────────────────────────────────

    private fun applySgr(args: IntArray) {
        if (args.isEmpty()) {
            pen.blank()
            return
        }
        var i = 0
        while (i < args.size) {
            val p = args[i]
            when {
                p == 0 -> pen.blank()
                p == 1 -> pen.bold = true
                p == 4 -> pen.underline = true
                p == 7 -> pen.inverse = true
                p == 22 -> pen.bold = false
                p == 24 -> pen.underline = false
                p == 27 -> pen.inverse = false
                p == 39 -> pen.fg = VT_DEFAULT_COLOR
                p == 49 -> pen.bg = VT_DEFAULT_COLOR
                p in 30..37 -> pen.fg = vtColor(p - 30)
                p in 90..97 -> pen.fg = vtColor(p - 90 + 8)
                p in 40..47 -> pen.bg = vtColor(p - 40)
                p in 100..107 -> pen.bg = vtColor(p - 100 + 8)
                p == 38 || p == 48 -> {
                    val mode = args.getOrNull(i + 1) ?: -1
                    var value = VT_DEFAULT_COLOR
                    when (mode) {
                        5 -> {
                            value = vtColor(args.getOrNull(i + 2) ?: 0)
                            i += 2
                        }
                        2 -> {
                            val r = args.getOrNull(i + 2) ?: 0
                            val g = args.getOrNull(i + 3) ?: 0
                            val b = args.getOrNull(i + 4) ?: 0
                            value = argb(r, g, b)
                            i += 4
                        }
                        else -> i += 1
                    }
                    if (p == 38) pen.fg = value else pen.bg = value
                }
            }
            i++
        }
    }

    // ── 写屏 ───────────────────────────────────────────────────────────────

    private fun putChar(c: Char) {
        if (c == '\u0000') return
        val wide = isWide(c)
        val width = if (wide) 2 else 1
        if (cursorCol + width > VT_MAX_COLS) {
            cursorCol = 0
            newline()
        }
        val line = lineAtMutable(cursorRow)
        while (line.size < cursorCol + width) line.add(VtCell())
        val cell = line[cursorCol]
        cell.ch = c
        cell.copyStyle(pen)
        if (wide) {
            val tail = line[cursorCol + 1]
            tail.ch = '\u0000'
            tail.copyStyle(pen)
        }
        cursorCol += width
    }

    private fun newline() {
        cursorRow++
        lineAtMutable(cursorRow)
        trim()
    }

    private fun moveTo(row: Int, col: Int) {
        cursorRow = row.coerceAtLeast(0)
        cursorCol = col.coerceIn(0, VT_MAX_COLS - 1)
        lineAtMutable(cursorRow)
        trim()
    }

    private fun lineAtMutable(index: Int): MutableList<VtCell> {
        while (lines.size <= index) lines.add(ArrayList())
        return lines[index]
    }

    private fun trim() {
        while (lines.size > maxLines) {
            lines.removeAt(0)
            baseIndex++
            if (cursorRow > 0) cursorRow--
        }
    }

    private fun eraseLine(mode: Int) {
        if (cursorRow !in lines.indices) return
        val line = lines[cursorRow]
        if (mode == 2) {
            for (cell in line) cell.blank()
            return
        }
        val from = if (mode == 1) 0 else cursorCol
        val to = if (mode == 1) cursorCol else line.size - 1
        var i = from
        while (i <= to && i < line.size) {
            line[i].blank()
            i++
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            1 -> {
                eraseLine(1)
                if (cursorRow > 0) {
                    lines.subList(0, minOf(cursorRow, lines.size)).clear()
                    cursorRow = 0
                }
            }
            2 -> {
                lines.clear()
                cursorRow = 0
                cursorCol = 0
            }
            else -> {
                eraseLine(0)
                if (cursorRow + 1 < lines.size) {
                    lines.subList(cursorRow + 1, lines.size).clear()
                }
            }
        }
    }
}

/** 16 色基础调色板（ARGB Int，按深色底调过亮度，黑色给成可读灰）。 */
private val VT_PALETTE = intArrayOf(
    0xFF6E7A8A.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
    0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFD9E2EC.toInt(),
    0xFF9AA5B4.toInt(), 0xFFFF8F8F.toInt(), 0xFFA9E396.toInt(), 0xFFFFD08A.toInt(),
    0xFF7CC3FF.toInt(), 0xFFD8A0F0.toInt(), 0xFF7FE0EA.toInt(), 0xFFFFFFFF.toInt(),
)

internal fun vtColor(index: Int): Int {
    if (index in 0..15) return VT_PALETTE[index]
    if (index in 16..231) {
        val i = index - 16
        return argb(cubeLevel(i / 36), cubeLevel((i % 36) / 6), cubeLevel(i % 6))
    }
    if (index in 232..255) {
        val gray = 8 + (index - 232) * 10
        return argb(gray, gray, gray)
    }
    return VT_PALETTE[7]
}

private fun cubeLevel(v: Int): Int = if (v == 0) 0 else 55 + v * 40

private fun argb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

/** 粗略宽字符判定（CJK / 全角标点 / 韩文），够用来画对齐的终端网格。 */
internal fun isWide(c: Char): Boolean {
    val code = c.code
    return code in 0x1100..0x115F ||
        code in 0x2E80..0xA4CF ||
        code in 0xAC00..0xD7A3 ||
        code in 0xF900..0xFAFF ||
        code in 0xFE30..0xFE6F ||
        code in 0xFF00..0xFF60 ||
        code in 0xFFE0..0xFFE6
}
