package com.hoerire.tools.zafiro.repo

import com.hoerire.tools.zafiro.repo.SettingsJsonCodecUtils.array
import com.hoerire.tools.zafiro.repo.SettingsJsonCodecUtils.orEmptyObjects
import com.hoerire.tools.zafiro.repo.SettingsJsonCodecUtils.parseObject
import com.hoerire.tools.zafiro.repo.SettingsJsonCodecUtils.string
import com.hoerire.tools.zafiro.repo.SettingsJsonCodecUtils.stringArray
import com.hoerire.tools.zafiro.repo.SettingsJsonCodecUtils.stringValues
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.hoerire.tools.zafiro.settings.model.RuntimeExecutionRule as ExecutionRule
import com.hoerire.tools.zafiro.settings.model.RuntimeExecutionRuleEnabledMode as ExecutionRuleEnabledMode

internal object RuleSettingsCodec {
    fun parseExecutionRules(json: String): List<ExecutionRule> {
        return parseObject(json)
            .array(RULES_KEY)
            .orEmptyObjects()
            .mapNotNull { obj ->
                val id = obj.string(ID_KEY).trim()
                val name = obj.string(NAME_KEY).trim()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                ExecutionRule(
                    id = id,
                    name = name,
                    enabledMode = ExecutionRuleEnabledMode.entries.firstOrNull {
                        it.name == obj.string(ENABLED_MODE_KEY)
                    } ?: ExecutionRuleEnabledMode.DISABLED,
                    patterns = obj.array(PATTERNS_KEY).stringValues(),
                    toolFilter = obj.array(TOOL_FILTER_KEY).stringValues(),
                    hint = obj.string(HINT_KEY).trim(),
                )
            }
    }

    fun encodeExecutionRules(rules: List<ExecutionRule>): String {
        return JsonObject(
            mapOf(
                RULES_KEY to JsonArray(
                    rules.map { rule ->
                        JsonObject(
                            mapOf(
                                ID_KEY to JsonPrimitive(rule.id),
                                NAME_KEY to JsonPrimitive(rule.name),
                                ENABLED_MODE_KEY to JsonPrimitive(rule.enabledMode.name),
                                PATTERNS_KEY to stringArray(rule.patterns),
                                TOOL_FILTER_KEY to stringArray(rule.toolFilter),
                                HINT_KEY to JsonPrimitive(rule.hint),
                            )
                        )
                    }
                )
            )
        ).toString()
    }

    private const val RULES_KEY = "rules"
    private const val ID_KEY = "id"
    private const val NAME_KEY = "name"
    private const val ENABLED_MODE_KEY = "enabled_mode"
    private const val PATTERNS_KEY = "patterns"
    private const val TOOL_FILTER_KEY = "tool_filter"
    private const val HINT_KEY = "hint"
}
