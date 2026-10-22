package com.hoerire.tools.zafiro.api.text

import com.hoerire.tools.zafiro.api.text.TurnTextComposer.cutLeadingBlocks

object TurnTextComposer {

    /** tag 前缀。需求方定的是 `zfr-`，不是 `zafiro-`。 */
    const val TAG_PREFIX = "zfr-"

    /** 一个被切下来的注入块。 */
    data class Block(val tag: String, val body: String)

    /** 切头的结果：[text] 是剩下的人话，[blocks] 是被摘掉的块（按出现顺序）。 */
    data class Cut(val text: String, val blocks: List<Block>)

    /** 把载荷包成块。tag 传 `files`（不带前缀）。 */
    fun wrapBlock(tag: String, body: String): String =
        "<$TAG_PREFIX$tag>\n$body\n</$TAG_PREFIX$tag>"

    /**
     * 反复吃掉开头的注入块，返回剩下的文本与被摘掉的块。
     *
     * 只认**完整**的块：开标签独占第一行、且后面能找到独占一行的配对闭标签。
     * 找不到闭标签就当这段文本是用户自己写的，整段原样返回（不做任何猜测）。
     * 一个块都没切到时返回的 [Cut.text] 与入参**逐字节相同**。
     */
    fun cutLeadingBlocks(text: String): Cut {
        if (text.isEmpty()) return Cut(text, emptyList())
        val lines = text.split('\n')
        val blocks = mutableListOf<Block>()
        var index = 0
        while (index < lines.size) {
            val tag = openTagOrNull(lines[index]) ?: break
            val close = "</$TAG_PREFIX$tag>"
            val closeIndex =
                (index + 1 until lines.size).firstOrNull { lines[it] == close } ?: break
            blocks += Block(
                tag = tag,
                body = lines.subList(index + 1, closeIndex).joinToString("\n")
            )
            index = closeIndex + 1
            // 块与正文之间的那个空行是拼装时加的，一起吃掉
            if (index < lines.size && lines[index].isBlank()) index++
        }
        if (blocks.isEmpty()) return Cut(text, emptyList())
        return Cut(lines.subList(index, lines.size).joinToString("\n"), blocks)
    }

    /** `<zfr-files>` → `files`；不是独占一行的合法开标签则返回 null。 */
    private fun openTagOrNull(line: String): String? {
        if (!line.startsWith("<$TAG_PREFIX") || !line.endsWith(">")) return null
        val tag = line.substring(TAG_PREFIX.length + 1, line.length - 1)
        if (tag.isEmpty() || !tag.all(::isTagChar)) return null
        return tag
    }

    private fun isTagChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '-' || char == '_'
}
