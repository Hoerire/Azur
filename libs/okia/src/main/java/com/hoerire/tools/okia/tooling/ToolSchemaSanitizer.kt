package com.hoerire.tools.okia.tooling

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal object ToolSchemaSanitizer {

    fun sanitize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            val isObject = isObjectSchema(element)
            element.forEach { (key, value) ->
                if (key == "pattern" && value is JsonPrimitive && value.isString) {
                    val pattern = value.content
                    if (isSafePattern(pattern)) {
                        put(key, value)
                    }
                } else if (key == "required" && isObject) {
                    if (value is JsonArray) {
                        put(key, sanitize(value))
                    } else {
                        // 遇到非数组或 null 的 required，纠偏为空数组
                        put(key, buildJsonArray { })
                    }
                } else {
                    put(key, sanitize(value))
                }
            }
            if (isObject && !element.containsKey("required")) {
                put("required", buildJsonArray { })
            }
        }

        is JsonArray -> buildJsonArray {
            element.forEach { add(sanitize(it)) }
        }

        else -> element
    }

    private fun isObjectSchema(element: JsonObject): Boolean {
        val type = element["type"]
        return when (type) {
            is JsonPrimitive -> type.isString && type.content == "object"
            is JsonArray -> type.any { (it as? JsonPrimitive)?.content == "object" }
            else -> false
        }
    }

    private fun isSafePattern(pattern: String): Boolean {
        // 包含 NUL 字节或被转义的 NUL (\0) 会被多数模型端（如 OpenAI）拒绝
        if (pattern.contains("\u0000") || pattern.contains("\\0")) return false
        return try {
            Regex(pattern)
            true
        } catch (_: Exception) {
            false
        }
    }
}
