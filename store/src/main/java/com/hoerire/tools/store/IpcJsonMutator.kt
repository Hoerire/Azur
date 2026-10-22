package com.hoerire.tools.store

import com.hoerire.tools.zafiro.api.util.tryOrNull
import com.hoerire.tools.logging.Logger
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 本地版 xTry（原 xposed-api 工具）：吞异常 + 记一行警告，失败返回 null。 */
private fun <T> xTry(name: String = "xTry", block: () -> T): T? =
    runCatching(block)
        .onFailure { Logger.w("niki914_store_xTry", "$name failed: ${it.message}") }
        .getOrNull()

internal object IpcJsonMutator {

    fun mutate(
        json: String,
        path: String,
        valueJson: String
    ): String {
        val root = parseJsonObject(json)
        val segments = path
            .split(".")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        require(segments.isNotEmpty()) { "key must not be blank" }

        var current = root
        segments.dropLast(1).forEach { segment ->
            val next = current.optJSONObject(segment) ?: JSONObject().also {
                current.put(segment, it)
            }
            current = next
        }
        current.put(segments.last(), toJsonValue(parseValueJson(valueJson)))
        return root.toString()
    }

    private fun parseJsonObject(json: String): JSONObject {
        return tryOrNull {
            if (json.isBlank()) JSONObject() else JSONObject(json)
        } ?: JSONObject()
    }

    private fun parseValueJson(valueJson: String): Any? {
        return tryOrNull {
            val tokener = JSONTokener(valueJson)
            val value = tokener.nextValue()
            check(tokener.nextClean() == 0.toChar())
            value
        }
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            JSONObject.NULL -> JSONObject.NULL
            is JSONObject -> value
            is JSONArray -> value
            is Boolean, is Number, is String -> value
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, nestedValue) ->
                    if (key != null) {
                        put(key.toString(), toJsonValue(nestedValue))
                    }
                }
            }

            is Iterable<*> -> JSONArray().apply {
                value.forEach { put(toJsonValue(it)) }
            }

            is Array<*> -> JSONArray().apply {
                value.forEach { put(toJsonValue(it)) }
            }

            else -> value.toString()
        }
    }
}
