package com.hoerire.tools.zafiro.chat.agentic

import com.hoerire.tools.logging.Logger
import com.hoerire.tools.zafiro.chat.ResolvedTools
import com.hoerire.tools.zafiro.settings.RuntimeEnvironment
import com.hoerire.tools.zafiro.settings.model.RuntimeLlmConfig
import com.hoerire.tools.zafiro.settings.model.RuntimeSkillMetadata

data class PromptComposeResult(
    val finalSystemPrompt: String,
)

data class PromptComposerInput(
    val additionalInstructions: String,
    val memoryItems: List<String> = emptyList(),
    val tools: ResolvedTools = ResolvedTools(),
    val enabledSkills: List<RuntimeSkillMetadata> = emptyList(),
    val sandboxPaths: Set<String> = emptySet(),
)

class PromptComposer {

    fun compose(input: PromptComposerInput): PromptComposeResult {
        val sections = stableSections(input)
        val volatile = buildVolatileTier(input)
        val finalSystemPrompt = (sections.map { it.second } + listOfNotNull(volatile))
            .joinToString(separator = "\n\n")

        // 固定开销分段计量：只报长度，不报内容（技能描述/记忆可能含隐私）
        val detail = sections.joinToString(separator = " ") { "${it.first}=${it.second.length}" }
        Logger.i(
            LOG_TAG,
            "prompt sections $detail volatile=${volatile?.length ?: 0} " +
                    "total=${finalSystemPrompt.length} estTokens=${(finalSystemPrompt.length / 4.0).toLong()}",
        )
        return PromptComposeResult(finalSystemPrompt = finalSystemPrompt)
    }

    // --- Stable tier: identity, tools, skills, guidance (cacheable across turns) ---

    private fun stableSections(input: PromptComposerInput): List<Pair<String, String>> {
        val identity = input.additionalInstructions.trim().ifBlank { DEFAULT_AGENT_IDENTITY }
        return listOfNotNull(
            "identity" to identity,
            renderEnvironmentBlock(input.sandboxPaths)?.let { "env" to it },
            renderToolContext(input.tools)?.let { "tools" to it },
            renderSkillContext(input.enabledSkills)
                .takeIf { hasBuiltinTool(input, "load_skill") }?.let { "skills" to it },
            TASK_COMPLETION_GUIDANCE.takeIf { hasAnyTool(input) }?.let { "task" to it },
            TOOL_USE_ENFORCEMENT_GUIDANCE.takeIf { hasAnyTool(input) }?.let { "tooluse" to it },
            EXECUTION_RULES_GUIDANCE.takeIf { hasAnyTool(input) }?.let { "rules" to it },
            MEMORY_GUIDANCE.takeIf { hasBuiltinTool(input, "memory") }?.let { "memoryGuide" to it },
            SKILLS_GUIDANCE.takeIf { hasBuiltinTool(input, "load_skill") }?.let { "skillsGuide" to it },
        )
    }

    // --- Volatile tier: memory snapshot (per-session) ---

    private fun buildVolatileTier(input: PromptComposerInput): String? {
        val items = input.memoryItems.map(String::trim).filter(String::isNotBlank)
        if (items.isEmpty()) return null
        val separator = "═".repeat(46)
        val content = items.joinToString("\n§\n")
        return "$separator\nMEMORY\n$separator\n$content"
    }

    // --- Tool context ---

    private fun renderToolContext(
        tools: ResolvedTools,
    ): String? {
        val blocks = listOfNotNull(
            renderNameBlock("builtin_tools", tools.builtinTools.map { it.name }),
            renderNameBlock("custom_py_tools", tools.customPyTools.map { it.name }),
        )
        if (blocks.isEmpty()) return null
        return "## Tool Context\n\n${blocks.joinToString(separator = "\n\n")}"
    }

    private fun renderNameBlock(tag: String, names: List<String>): String? {
        val normalized = names.map(String::trim).filter(String::isNotBlank).distinct().sorted()
        if (normalized.isEmpty()) return null
        return normalized.joinToString(
            separator = "\n",
            prefix = "<$tag>\n",
            postfix = "\n</$tag>",
        ) { "- $it" }
    }

    // --- Skill context ---

    private fun renderSkillContext(skills: List<RuntimeSkillMetadata>): String? {
        val entries = skills
            .mapNotNull { skill ->
                val id = skill.id.trim()
                if (id.isBlank()) return@mapNotNull null
                val name = skill.name.trim().ifBlank { id }
                val desc = skill.description.trim()
                val dir = skill.absoluteDir.trim()
                buildString {
                    appendLine("  <skill>")
                    appendLine("    <id>$id</id>")
                    appendLine("    <name>$name</name>")
                    if (desc.isNotBlank()) appendLine("    <description>$desc</description>")
                    if (dir.isNotBlank()) appendLine("    <dir>$dir</dir>")
                    append("  </skill>")
                }
            }
            .sorted()
        if (entries.isEmpty()) return null

        return buildString {
            appendLine("## Skills (mandatory)")
            appendLine()
            appendLine(
                "Before replying, scan the skills below. If a skill matches or is even partially " +
                        "relevant to your task, you MUST load it with load_skill and follow its " +
                        "instructions. Err on the side of loading — it is always better to have " +
                        "context you don't need than to miss critical steps, pitfalls, or established " +
                        "workflows. Skills contain specialized knowledge and proven approaches that " +
                        "outperform general-purpose methods."
            )
            appendLine()
            appendLine("<available_skills>")
            entries.forEach { appendLine(it) }
            append("</available_skills>")
        }
    }

    // --- Environment block ---

    /** 私有存储路径提示块：告诉 Agent 这些目录可自由读写，零权限。 */
    private fun renderEnvironmentBlock(paths: Set<String>): String? {
        val hostTools = RuntimeEnvironment.hostToolsStatus
        if (paths.isEmpty() && hostTools == null) return null
        val pathList = paths.sorted().joinToString("\n") { "- $it" }
        return buildString {
            appendLine("# Environment")
            appendLine()
            appendLine("Private app storage (read/write freely, no storage permission required):")
            appendLine(pathList)
            appendLine()
            appendLine("Files outside these directories are shared storage and may require")
            appendLine("storage permission that is not currently granted — prefer private")
            appendLine("directories. If a task requires public files, tell the user.")
            hostTools?.let {
                appendLine()
                appendLine("Host tools (files/hosttools/bin/*): $it")
                appendLine(
                    "If this line is not \"ok\", binaries under files/hosttools (python3/rg/jq/7zz/curl) " +
                        "cannot be executed on this device: fall back to /system/bin/sh and tell the user instead of retrying."
                )
            }
        }
    }

    // --- Helpers ---

    private fun hasBuiltinTool(input: PromptComposerInput, name: String): Boolean {
        return input.tools.builtinTools.any { it.name == name }
    }

    private fun hasAnyTool(input: PromptComposerInput): Boolean {
        return input.tools.builtinTools.isNotEmpty() ||
                input.tools.customPyTools.isNotEmpty()
    }

    // --- Guidance constants ---

    companion object {
        internal const val LOG_TAG = "zafiro_PromptComposer"

        fun buildMemoryItems(config: RuntimeLlmConfig): List<String> {
            val memories = config.memories.map(String::trim).filter(String::isNotBlank)
            if (memories.isNotEmpty()) {
                return memories
            }
            return listOfNotNull(config.memoryPrompt.trim().takeIf { it.isNotBlank() })
        }

        internal const val DEFAULT_AGENT_IDENTITY =
            "You are Anvil, an intelligent AI assistant. " +
                    "You are helpful, knowledgeable, and direct. " +
                    "You assist users with a wide range of tasks including answering questions, " +
                    "managing their device, and executing actions via your tools. " +
                    "You communicate clearly, admit uncertainty when appropriate, and prioritize " +
                    "being genuinely useful over being verbose."

        internal const val TASK_COMPLETION_GUIDANCE =
            "# Finishing the job\n" +
                    "When the user asks you to build, run, or verify something, the deliverable is " +
                    "a working result backed by real tool output — not a description of one. " +
                    "Do not stop after writing a stub, a plan, or a single command. Keep working " +
                    "until you have actually exercised the code or produced the requested result, " +
                    "then report what real execution returned.\n" +
                    "If a tool, install, or network call fails and blocks the real path, say so " +
                    "directly and try an alternative (different package manager, different " +
                    "approach, ask the user). NEVER substitute plausible-looking fabricated " +
                    "output (made-up data, invented file contents, synthesised API responses) " +
                    "for results you couldn't actually produce. Reporting a blocker honestly " +
                    "is always better than inventing a result."

        internal const val TOOL_USE_ENFORCEMENT_GUIDANCE =
            "# Tool use\n" +
                    "You MUST use your tools to take action — do not describe what you would do " +
                    "without actually doing it. When you say you will perform an action, you MUST " +
                    "immediately make the corresponding tool call in the same response. Never end " +
                    "your turn with a promise of future action — execute it now.\n" +
                    "Every response should either (a) contain tool calls that make progress, or " +
                    "(b) deliver a final result to the user."

        internal const val EXECUTION_RULES_GUIDANCE =
            "# Execution rules\n" +
                    "Tool actions may be blocked by Anvil's app-level execution rules, which are " +
                    "user-configurable in Anvil settings — not system restrictions. A block means " +
                    "the user declined the action or the rule is too strict; the user can adjust " +
                    "the rule in Anvil settings. Do not describe blocks as system policy."

        internal const val MEMORY_GUIDANCE =
            "You have persistent memory across sessions. Save durable facts using the memory " +
                    "tool: user preferences, environment details, tool quirks, and stable conventions. " +
                    "Memory is injected into every turn, so keep it compact and focused on facts that " +
                    "will still matter later.\n" +
                    "Prioritize what reduces future user steering — the most valuable memory is one " +
                    "that prevents the user from having to correct or remind you again. " +
                    "User preferences and recurring corrections matter more than procedural task details.\n" +
                    "Do NOT save task progress, session outcomes, completed-work logs, or temporary TODO " +
                    "state to memory; use conversation history to recall those from past interactions. " +
                    "Specifically: do not record PR numbers, issue numbers, commit SHAs, 'fixed bug X', " +
                    "'submitted PR Y', 'Phase N done', file counts, or any artifact that will be stale " +
                    "in 7 days. If a fact will be stale in a week, it does not belong in memory. " +
                    "If you've discovered a new way to do something, solved a problem that could be " +
                    "necessary later, save it as a skill with python — write a SKILL.md under " +
                    "the skills directory (see the skill-creator skill).\n" +
                    "Write memories as declarative facts, not instructions to yourself. " +
                    "'User prefers concise responses' ✓ — 'Always respond concisely' ✗. " +
                    "'Project uses pytest with xdist' ✓ — 'Run tests with pytest -n 4' ✗. " +
                    "Imperative phrasing gets re-read as a directive in later sessions and can " +
                    "cause repeated work or override the user's current request. Procedures and " +
                    "workflows belong in skills, not memory."

        internal const val SKILLS_GUIDANCE =
            "# Skills\n" +
                    "Skills contain specialized knowledge — API endpoints, tool-specific " +
                    "commands, and proven workflows that outperform general-purpose approaches. " +
                    "Load the skill even if you think you could handle the task with basic " +
                    "tools. Skills also encode the user's preferred approach, conventions, " +
                    "and quality standards — load them even for tasks you already know how " +
                    "to do, because the skill defines how it should be done here.\n" +
                    "load_skill returns the skill's SKILL.md content; if it exceeds the limit, " +
                    "the result ends with the absolute path to the file — use shell to read " +
                    "the full content from there.\n" +
                    "构建 / 编译 / 打包 / 出 APK / 工具链 / gcc·clang·cmake / 环境缺失 这类任务，" +
                    "先 load_skill(\"build-env\")；环境未部署时 zafiro_env 会自动从 Download 解压扩展包，不要手工摸盘。"
    }
}
