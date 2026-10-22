package com.hoerire.tools.zafiro.chat

import com.hoerire.tools.zafiro.chat.agentic.buildin.BuiltinToolRegistry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinToolTest {
    @Test
    fun defaultRegistry_containsExpectedTools() {
        val registry = BuiltinToolRegistry.default()

        assertEquals(
            listOf(
                "find_installed_apps",
                "launch_app",
                "load_skill",
                "memory",
                "notify",
                "open_uri",
                "screen_operation_accessibility",
                "screen_operation_shell",
                "screenshot",
                "shell",
                "zafiro_env", "view_image",
            ),
            registry.all().map { it.name }.sorted()
        )
        assertEquals("launch_app", registry.find("launch_app")?.name)
        assertEquals("load_skill", registry.find("load_skill")?.name)
        assertEquals("memory", registry.find("memory")?.name)
        assertEquals("notify", registry.find("notify")?.name)
        assertEquals("open_uri", registry.find("open_uri")?.name)
        assertEquals(
            "screen_operation_accessibility",
            registry.find("screen_operation_accessibility")?.name
        )
        assertEquals("screen_operation_shell", registry.find("screen_operation_shell")?.name)
        assertEquals("find_installed_apps", registry.find("find_installed_apps")?.name)
        assertEquals("shell", registry.find("shell")?.name)
    }

    @Test
    fun toolSchemas_areValidJsonSchema() {
        BuiltinToolRegistry.default().all().forEach { tool ->
            assertTrue("description not blank: ${tool.name}", tool.description.isNotBlank())
            assertSchemaParsable(tool.inputSchemaJson, tool.name)
        }
    }

    private fun assertSchemaParsable(schemaJson: String?, toolName: String) {
        assertNotNull("schema missing for $toolName", schemaJson)
        try {
            Json.parseToJsonElement(schemaJson!!).jsonObject
        } catch (e: SerializationException) {
            throw AssertionError("schema of $toolName is not JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw AssertionError("schema of $toolName is not JSON: ${e.message}", e)
        }
    }
}
