package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.zafiro.app.ui.model.SettingsViewModel
import com.hoerire.tools.zafiro.app.ui.nav.BuiltinToolGroupDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ExecutionRuleDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.PromptEditPage
import com.hoerire.tools.zafiro.app.ui.nav.SavedConfigDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.SettingsProviderPickPage
import com.hoerire.tools.zafiro.app.ui.nav.SkillDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroSettingsGroup

@Composable
fun SettingsDetailPageContent(
    group: ZafiroSettingsGroup,
    onPush: (ZafiroPage) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = pageViewModel<SettingsViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val visibleGroups = uiState.sections.flatMap { it.groups }.toSet()
    if (group !in visibleGroups) {
        return
    }

    if (group == ZafiroSettingsGroup.ModelConfig) {
        ModelConfigSettingsContent(
            onBack = onBack,
            onOpenProviderPick = {
                onPush(SettingsProviderPickPage)
            },
            onOpenConfigDetail = { configId, configName ->
                onPush(SavedConfigDetailPage(configId = configId, configName = configName))
            },
            onOpenPromptEdit = {
                onPush(PromptEditPage)
            },
        )
        return
    }

    if (group == ZafiroSettingsGroup.Tools) {
        BuiltinToolsSettingsContent(
            onOpenGroupDetail = { groupId ->
                onPush(BuiltinToolGroupDetailPage(groupId))
            },
        )
        return
    }

    if (group == ZafiroSettingsGroup.Skills) {
        SkillsSettingsContent(
            onOpenSkillDetail = { id, title ->
                onPush(SkillDetailPage(id, title))
            },
        )
        return
    }
    if (group == ZafiroSettingsGroup.Theme) {
        ThemeSettingsContent(onPush = onPush)
        return
    }

    if (group == ZafiroSettingsGroup.GeneralSettings) {
        GeneralSettingsContent()
        return
    }

    if (group == ZafiroSettingsGroup.Storage) {
        StorageSettingsContent()
        return
    }

    if (group == ZafiroSettingsGroup.About) {
        AboutSettingsContent()
        return
    }

    if (group == ZafiroSettingsGroup.Memory) {
        MemorySettingsContent()
        return
    }

    if (group == ZafiroSettingsGroup.RuntimeControl) {
        RuntimeControlSettingsContent()
        return
    }

    if (group == ZafiroSettingsGroup.ExecutionRules) {
        ExecutionRulesSettingsContent(
            onOpenRuleDetail = { name, index, isCreating ->
                onPush(ExecutionRuleDetailPage(name, index, isCreating))
            },
        )
        return
    }

    TODOPageContent()
    return
}
