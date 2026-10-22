package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.hoerire.tools.uikit.infra.component.settings.SettingsPageSpec
import com.hoerire.tools.uikit.infra.component.settings.SettingsRowAction
import com.hoerire.tools.uikit.infra.component.settings.SettingsRowSpec
import com.hoerire.tools.uikit.infra.component.settings.SettingsSectionLayout
import com.hoerire.tools.uikit.infra.component.settings.SettingsSectionSpec
import com.hoerire.tools.uikit.infra.component.settings.SettingsSpecPageContent
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.model.BuiltinToolGroupDetailIntent
import com.hoerire.tools.zafiro.app.ui.model.BuiltinToolGroupDetailUiState
import com.hoerire.tools.zafiro.app.ui.model.BuiltinToolGroupDetailViewModel
import com.hoerire.tools.zafiro.app.ui.model.ToolPresentation
import com.hoerire.tools.zafiro.app.ui.nav.BuiltinToolGroupDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage

@Composable
fun BuiltinToolGroupDetailContent(
    page: BuiltinToolGroupDetailPage,
    onBack: () -> Unit,
    onPush: (ZafiroPage) -> Unit,
) {
    val viewModel = pageViewModel<BuiltinToolGroupDetailViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(page.groupId) {
        viewModel.sendIntent(BuiltinToolGroupDetailIntent.Load(page.groupId))
    }

    SettingsSpecPageContent(
        spec = builtinToolGroupDetailSpec(uiState),
        onAction = { action ->
            when (action) {
                is SettingsRowAction.ToggleChanged -> viewModel.sendIntent(
                    BuiltinToolGroupDetailIntent.ItemEnabledChanged(
                        name = action.id,
                        value = action.checked,
                    )
                )

                is SettingsRowAction.Navigate -> Unit

                else -> Unit
            }
        },
    )
}

@Composable
private fun builtinToolGroupDetailSpec(uiState: BuiltinToolGroupDetailUiState): SettingsPageSpec {
    val sections = if (!uiState.isLoading && uiState.members.isNotEmpty()) {
        listOf(
            SettingsSectionSpec(
                layout = SettingsSectionLayout.GroupedCard,
                rows = uiState.members.map { item ->
                    val displayName =
                        ToolPresentation.displayNameResOf(item.name)?.let { stringResource(it) }
                            ?: item.name
                    SettingsRowSpec.Toggle(
                        id = item.name,
                        title = displayName,
                        summary = item.description,
                        checked = item.enabled,
                        enabled = !uiState.isSaving,
                    )
                },
            ),
        )
    } else {
        emptyList()
    }

    return SettingsPageSpec(
        description = stringResource(R.string.tool_page_description),
        sections = sections,
    )
}

