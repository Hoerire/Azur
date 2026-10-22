package com.hoerire.tools.zafiro.app.ui.model

import androidx.annotation.StringRes
import com.hoerire.tools.logging.Logger
import com.hoerire.tools.uikit.base.ComposeMVIViewModel
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroSettingsGroup

data class SettingsSectionUiState(
    @StringRes val titleRes: Int,
    val groups: List<ZafiroSettingsGroup>,
)

data class SettingsUiState(
    val sections: List<SettingsSectionUiState> = emptyList(),
)

sealed interface SettingsIntent

sealed interface SettingsEffect

class SettingsViewModel : ComposeMVIViewModel<SettingsIntent, SettingsUiState, SettingsEffect>() {

    override fun initUiState(): SettingsUiState {
        val state = buildSettingsUiState(defaultHiddenSettingsGroups())
        Logger.d(
            LOG_TAG,
            "initUiState sections=${state.sections.size} " +
                    "groups=${state.sections.sumOf { it.groups.size }}"
        )
        return state
    }

    override suspend fun handleIntent(intent: SettingsIntent) = Unit

    private companion object {
        private const val LOG_TAG = "zafiro_SettingsViewModel"
    }
}

private data class SettingsSectionDefinition(
    @StringRes val titleRes: Int,
    val groups: List<ZafiroSettingsGroup>,
)

internal fun buildSettingsUiState(
    hiddenGroups: Set<ZafiroSettingsGroup>,
): SettingsUiState {
    return SettingsUiState(
        sections = settingsSections()
            .mapNotNull { section ->
                val groups = section.groups.filterNot(hiddenGroups::contains)
                groups.takeIf { it.isNotEmpty() }?.let {
                    SettingsSectionUiState(
                        titleRes = section.titleRes,
                        groups = groups,
                    )
                }
            }
    )
}

private fun settingsSections(): List<SettingsSectionDefinition> {
    return listOf(
        SettingsSectionDefinition(
            titleRes = R.string.ui_settings_section_model,
            groups = listOf(
                ZafiroSettingsGroup.ModelConfig,
                ZafiroSettingsGroup.Memory,
                ZafiroSettingsGroup.RuntimeControl,
            ),
        ),
        SettingsSectionDefinition(
            titleRes = R.string.ui_settings_section_general,
            groups = listOf(
                ZafiroSettingsGroup.Theme,
                ZafiroSettingsGroup.GeneralSettings,
            ),
        ),
        SettingsSectionDefinition(
            titleRes = R.string.ui_settings_section_ability,
            groups = listOf(
                ZafiroSettingsGroup.Tools,
                ZafiroSettingsGroup.Skills,
            ),
        ),
        SettingsSectionDefinition(
            titleRes = R.string.ui_settings_section_rules,
            groups = listOf(
                ZafiroSettingsGroup.ExecutionRules,
            ),
        ),
        SettingsSectionDefinition(
            titleRes = R.string.ui_settings_section_app,
            groups = listOf(
                ZafiroSettingsGroup.Storage,
                ZafiroSettingsGroup.About,
            ),
        ),
    )
}

private fun defaultHiddenSettingsGroups(): Set<ZafiroSettingsGroup> {
    return emptySet()
}
