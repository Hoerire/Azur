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
import com.hoerire.tools.zafiro.app.ui.model.BuiltinToolGroupUiItem
import com.hoerire.tools.zafiro.app.ui.model.BuiltinToolSettingsIntent
import com.hoerire.tools.zafiro.app.ui.model.BuiltinToolSettingsUiState
import com.hoerire.tools.zafiro.app.ui.model.BuiltinToolSettingsViewModel
import com.hoerire.tools.zafiro.repo.BuiltinToolGroupMode

private const val GROUP_ID_PREFIX = "group:"

@Composable
fun BuiltinToolsSettingsContent(
    onOpenGroupDetail: (groupId: String) -> Unit,
) {
    val viewModel = pageViewModel<BuiltinToolSettingsViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(BuiltinToolSettingsIntent.Load)
    }

    BuiltinToolsSettingsContentBody(
        uiState = uiState,
        onItemEnabledChange = { name, checked ->
            viewModel.sendIntent(
                BuiltinToolSettingsIntent.ItemEnabledChanged(
                    name = name,
                    value = checked,
                )
            )
        },
        onGroupToggled = { groupId, checked ->
            viewModel.sendIntent(
                BuiltinToolSettingsIntent.GroupToggled(
                    groupId = groupId,
                    value = checked,
                )
            )
        },
        onOpenGroupDetail = onOpenGroupDetail,
    )
}

@Composable
private fun BuiltinToolsSettingsContentBody(
    uiState: BuiltinToolSettingsUiState,
    onItemEnabledChange: (String, Boolean) -> Unit,
    onGroupToggled: (String, Boolean) -> Unit,
    onOpenGroupDetail: (String) -> Unit,
) {
    SettingsSpecPageContent(
        spec = builtinToolsSettingsSpec(uiState),
        onAction = { action ->
            when (action) {
                is SettingsRowAction.ToggleChanged -> {
                    if (action.id.startsWith(GROUP_ID_PREFIX)) {
                        onGroupToggled(action.id.removePrefix(GROUP_ID_PREFIX), action.checked)
                    } else {
                        onItemEnabledChange(action.id, action.checked)
                    }
                }

                is SettingsRowAction.Navigate -> {
                    val groupId = action.id.removePrefix(GROUP_ID_PREFIX)
                    if (action.id.startsWith(GROUP_ID_PREFIX)) {
                        onOpenGroupDetail(groupId)
                    }
                }

                else -> Unit
            }
        },
    )
}

@Composable
private fun builtinToolsSettingsSpec(uiState: BuiltinToolSettingsUiState): SettingsPageSpec {
    val rows = uiState.groups.map { group ->
        group.toRow(enabled = !uiState.isSaving)
    } + uiState.standaloneTools.map { item ->
        SettingsRowSpec.Toggle(
            id = item.name,
            title = item.name,
            summary = item.description,
            checked = item.enabled,
            enabled = !uiState.isSaving,
        )
    }

    return SettingsPageSpec(
        description = builtinToolDescription(uiState),
        sections = if (rows.isNotEmpty()) {
            listOf(
                SettingsSectionSpec(
                    layout = SettingsSectionLayout.GroupedCard,
                    rows = rows,
                ),
            )
        } else {
            emptyList()
        },
    )
}

@Composable
private fun BuiltinToolGroupUiItem.toRow(enabled: Boolean): SettingsRowSpec {
    return when (mode) {
        // 绑定式：与单独工具同款 Switch，点击写穿全组成员，无二级页
        BuiltinToolGroupMode.WHOLE -> SettingsRowSpec.Toggle(
            id = "$GROUP_ID_PREFIX$id",
            title = stringResource(titleRes),
            summary = stringResource(summaryRes),
            checked = checked,
            enabled = enabled,
        )

        // 不绑定：导航行进二级页逐工具开关
        BuiltinToolGroupMode.PER_TOOL -> SettingsRowSpec.Navigation(
            id = "$GROUP_ID_PREFIX$id",
            title = stringResource(titleRes),
            summary = stringResource(summaryRes),
            enabled = enabled,
        )

        // 平铺组不产生任何行（ViewModel 已过滤 FLAT，此处仅为穷尽 when）
        BuiltinToolGroupMode.FLAT -> SettingsRowSpec.Navigation(
            id = "$GROUP_ID_PREFIX$id",
            title = stringResource(titleRes),
            summary = stringResource(summaryRes),
            enabled = enabled,
        )
    }
}

@Composable
private fun builtinToolDescription(uiState: BuiltinToolSettingsUiState): String {
    val arg = uiState.descriptionArg
    return if (arg == null) {
        stringResource(uiState.descriptionResId)
    } else {
        stringResource(uiState.descriptionResId, arg)
    }
}

