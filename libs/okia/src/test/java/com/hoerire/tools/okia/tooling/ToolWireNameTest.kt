package com.hoerire.tools.okia.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolWireNameTest {

    // ── 本地工具 ───────────────────────────────────────────────────────────

    @Test
    fun forLocalKeepsDashAndUnderscoreAndReplacesOthers() {
        // - 与 _ 保留；点 / 空格替换为 _；纯符号生成确定性哈希
        assertEquals("admin_tools_list", ToolWireName.forLocal("admin.tools.list"))
        assertEquals("list-files", ToolWireName.forLocal("list-files"))
        assertEquals("get_user_info", ToolWireName.forLocal("get user info"))
        assertTrue(ToolWireName.forLocal("!!").startsWith("tool_"))
    }

    @Test
    fun forLocalTruncatesTo64() {
        val long = "t".repeat(200)
        val name = ToolWireName.forLocal(long)
        assertEquals(64, name.length)
        assertEquals("t".repeat(64), name)
    }

    // ── 消歧（D4 A：哈希后缀） ─────────────────────────────────────────────

}
