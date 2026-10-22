package com.hoerire.tools.zafiro.chat.agentic.buildin

import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.FindInstalledAppsBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.LaunchAppBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.LoadSkillBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.MemoryBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.NotifyBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.OpenUriBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.ScreenOperationAccessibilityBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.ScreenOperationShellBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.ScreenshotBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.ShellBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.ViewImageBuiltin
import com.hoerire.tools.zafiro.chat.agentic.buildin.impl.ZafiroEnvBuiltin

class BuiltinToolRegistry(
    private val tools: List<BuiltinTool>,
) {
    fun all(): List<BuiltinTool> = tools

    fun find(name: String): BuiltinTool? {
        return tools.firstOrNull { it.name == name }
    }

    companion object {
        fun default(): BuiltinToolRegistry = BuiltinToolRegistry(
            listOf(
                LaunchAppBuiltin(),
                MemoryBuiltin(),
                NotifyBuiltin(),
                OpenUriBuiltin(),
                LoadSkillBuiltin(),
                ShellBuiltin(),
                FindInstalledAppsBuiltin(),
                ScreenOperationAccessibilityBuiltin(),
                ScreenOperationShellBuiltin(),
                ScreenshotBuiltin(),
                ViewImageBuiltin(),
                ZafiroEnvBuiltin(),
            )
        )
    }
}
