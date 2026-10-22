package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hoerire.tools.uikit.infra.LiquidDialog
import com.hoerire.tools.uikit.infra.component.MaterialTintLiquidButton
import com.hoerire.tools.uikit.infra.component.settings.SettingsPageSpec
import com.hoerire.tools.uikit.infra.component.settings.SettingsRowAction
import com.hoerire.tools.uikit.infra.component.settings.SettingsRowSpec
import com.hoerire.tools.uikit.infra.component.settings.SettingsSectionLayout
import com.hoerire.tools.uikit.infra.component.settings.SettingsSectionSpec
import com.hoerire.tools.uikit.infra.component.settings.SettingsSpecPageContent
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.model.SettingsUiState
import com.hoerire.tools.zafiro.app.ui.model.SettingsViewModel
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroSettingsGroup

@Composable
fun SettingsHomePageContent(
    onOpenGroup: (ZafiroSettingsGroup) -> Unit,
) {
    val viewModel = pageViewModel<SettingsViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    SettingsHomePageContentBody(
        uiState = uiState,
        onOpenGroup = onOpenGroup,
    )
}

@Composable
private fun SettingsHomePageContentBody(
    uiState: SettingsUiState,
    onOpenGroup: (ZafiroSettingsGroup) -> Unit,
) {
    val groupsById = uiState.sections
        .flatMap { it.groups }
        .associateBy { it.name }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rootBlocked by remember { mutableStateOf(false) }

    SettingsSpecPageContent(
        spec = settingsHomePageSpec(uiState),
        onAction = { action ->
            when (action) {
                is SettingsRowAction.Navigate -> {
                    val group = groupsById[action.id]
                    group?.let(onOpenGroup)
                }
                else -> Unit
            }
        },
    )

    LiquidDialog(
        visible = rootBlocked,
        onDismissRequest = { rootBlocked = false },
        dismissOnBackgroundTap = false,
        title = { Text(stringResource(R.string.ui_settings_buildkit_root_title)) },
        text = { Text(stringResource(R.string.ui_settings_buildkit_root_text)) },
        actions = {
            MaterialTintLiquidButton(
                text = stringResource(R.string.ui_settings_buildkit_root_ok),
                onClick = { rootBlocked = false },
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun settingsHomePageSpec(
    uiState: SettingsUiState,
): SettingsPageSpec {
    return SettingsPageSpec(
        sections = uiState.sections.map { section ->
            SettingsSectionSpec(
                title = stringResource(section.titleRes),
                layout = SettingsSectionLayout.GroupedCard,
                rows = section.groups.map { group ->
                    SettingsRowSpec.Navigation(
                        id = group.name,
                        title = stringResource(group.titleRes),
                        summary = group.summaryRes?.let { stringResource(it) },
                    )
                },
            )
        },
    )
}

