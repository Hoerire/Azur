package com.hoerire.tools.okia.tooling

import com.hoerire.tools.okia.fake.RecordingToolExecutor
import com.hoerire.tools.okia.fake.localTool
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultToolRegistryTest {

    private val executor = RecordingToolExecutor()

    private fun names(registry: ToolRegistry): List<String> =
        registry.snapshot().map { it.descriptor.wireName }

    @Test
    fun snapshotOrderIsIndependentOfRegistrationOrder() {
        val forward = DefaultToolRegistry().apply {
            listOf("b_tool", "a_tool", "c_tool").forEach { register(localTool(it), executor) }
        }
        val reverse = DefaultToolRegistry().apply {
            listOf("c_tool", "a_tool", "b_tool").forEach { register(localTool(it), executor) }
        }

        assertEquals(listOf("a_tool", "b_tool", "c_tool"), names(forward))
        assertEquals(names(forward), names(reverse))
    }

}
