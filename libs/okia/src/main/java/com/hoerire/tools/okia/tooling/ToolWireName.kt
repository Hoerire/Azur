package com.hoerire.tools.okia.tooling

import java.lang.Long
import kotlin.Int
import kotlin.String

object ToolWireName {

    /** Provider function name 长度上限（OpenAI / Anthropic 均 64）。 */
    const val MAX_LENGTH = 64

    private const val SEP = "__"
    private const val HASH_HEX_LENGTH = 12
    private const val HASH_SUFFIX_LENGTH = HASH_HEX_LENGTH + 1 // "_" + 12 hex
    private const val MAX_SERVER_SEGMENT = 32

    /** 本地工具的线缆名：段规范化 + 长度截断。 */
    fun forLocal(name: String): String = truncate(sanitizeSegment(name, "tool"), MAX_LENGTH)

    /**
     * 在 used 集合内对 base 做唯一性消歧。
     * base 未占用且未超长 → 原样返回；否则追加 `_` + 哈希后缀（必要时尾部
     * 截断到 MAX_LENGTH），attempt 迭代（哈希输入掺入 attempt）保证最终返回
     * 一个不在 used 中的名字。rawIdentity 是工具的唯一确定性标识（如
     * `server\u0000tool`），保证同工具重复派生输出稳定。
     */
    fun disambiguate(base: String, rawIdentity: String, used: Set<String>): String {
        if (base.length <= MAX_LENGTH && base !in used) return base
        var attempt = 0
        while (true) {
            val identity = if (attempt == 0) rawIdentity else "$rawIdentity\u0000$attempt"
            val suffix = "_" + fnvHex(identity).takeLast(HASH_HEX_LENGTH)
            val budget = MAX_LENGTH - HASH_SUFFIX_LENGTH
            val candidate = truncate(base, budget) + suffix
            if (candidate !in used) return candidate
            attempt++
        }
    }

    private fun sanitizeSegment(name: String, prefix: String = "s"): String {
        if (name.isEmpty()) return "${prefix}_empty"
        val raw = buildString(name.length) {
            for (c in name) {
                append(
                    if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_') c
                    else '_'
                )
            }
        }
        val clean = raw.replace(Regex("_+"), "_").trim('_')
        return if (clean.any { it.isLetterOrDigit() }) {
            clean
        } else {
            "${prefix}_${fnvHex(name).take(8)}"
        }
    }

    private fun truncate(value: String, maxLen: Int): String =
        if (value.length <= maxLen) value else value.take(maxLen)

    /** FNV-1a 64 位哈希（确定性；hex 小写）。用于消歧后缀，无需密码学强度。 */
    private fun fnvHex(input: String): String {
        var hash = -3750763034362895579L // FNV-1a offset basis
        for (b in input.encodeToByteArray()) {
            hash = hash xor (b.toLong() and 0xffL)
            hash = hash * 1099511628211L // FNV-1a prime
        }
        return Long.toHexString(hash)
    }
}
