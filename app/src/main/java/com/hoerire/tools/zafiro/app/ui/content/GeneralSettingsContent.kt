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
import com.hoerire.tools.zafiro.app.ui.model.GeneralSettingsIntent
import com.hoerire.tools.zafiro.app.ui.model.GeneralSettingsViewModel

/**
 * General Settings：悬浮与通知、对话与交互 2 个分组。
 * 容器使用 SettingsSpecPageContent + GroupedCard，开关状态由 ViewModel 驱动。
 */
private const val FLOATING_BALL_ROW_ID = "general.floating_ball"
private const val FLOATING_BALL_AUTO_EXPAND_ROW_ID = "general.floating_ball_auto_expand"
private const val RESIDENT_NOTIFICATION_ROW_ID = "general.resident_notification"
private const val TURN_NOTIFICATION_ROW_ID = "general.turn_notification"
private const val LOAD_LAST_ROW_ID = "general.load_last"
private const val ALWAYS_SHOW_ACTIONS_ROW_ID = "general.always_show_message_actions"

@Composable
fun GeneralSettingsContent(
    viewModel: GeneralSettingsViewModel = pageViewModel(),
) {
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(GeneralSettingsIntent.Load)
    }

    val spec = SettingsPageSpec(
        description = null,
        sections = listOf(
            // 分组 1：悬浮与通知
            SettingsSectionSpec(
                title = stringResource(R.string.ui_settings_general_group_floating_notification),
                layout = SettingsSectionLayout.GroupedCard,
                rows = listOf(
                    SettingsRowSpec.Toggle(
                        id = FLOATING_BALL_ROW_ID,
                        title = stringResource(R.string.ui_settings_general_floating_ball),
                        summary = stringResource(R.string.ui_settings_general_floating_ball_summary),
                        checked = uiState.floatingBallEnabled,
                    ),
                    SettingsRowSpec.Toggle(
                        id = FLOATING_BALL_AUTO_EXPAND_ROW_ID,
                        title = stringResource(R.string.ui_settings_general_floating_ball_auto_expand),
                        summary = stringResource(R.string.ui_settings_general_floating_ball_auto_expand_summary),
                        checked = uiState.floatingBallAutoExpand,
                    ),
                    SettingsRowSpec.Toggle(
                        id = RESIDENT_NOTIFICATION_ROW_ID,
                        title = stringResource(R.string.ui_settings_general_resident_notification),
                        summary = stringResource(R.string.ui_settings_general_resident_notification_summary),
                        checked = uiState.residentNotificationEnabled,
                    ),
                    SettingsRowSpec.Toggle(
                        id = TURN_NOTIFICATION_ROW_ID,
                        title = stringResource(R.string.ui_settings_general_turn_notification),
                        summary = stringResource(R.string.ui_settings_general_turn_notification_summary),
                        checked = uiState.turnNotificationEnabled,
                    ),
                ),
            ),
            // 分组 3：对话与交互
            SettingsSectionSpec(
                title = stringResource(R.string.ui_settings_general_group_conversation),
                layout = SettingsSectionLayout.GroupedCard,
                rows = listOf(
                    SettingsRowSpec.Toggle(
                        id = LOAD_LAST_ROW_ID,
                        title = stringResource(R.string.ui_settings_general_load_last_conversation),
                        checked = uiState.loadLastConversation,
                    ),
                    SettingsRowSpec.Toggle(
                        id = ALWAYS_SHOW_ACTIONS_ROW_ID,
                        title = stringResource(R.string.ui_settings_general_always_show_message_actions),
                        checked = uiState.alwaysShowMessageActions,
                    ),
                ),
            ),
            // 分组 4：运行与控制已迁至独立页（RuntimeControl）
        ),
    )

    SettingsSpecPageContent(
        spec = spec,
        onAction = { action ->
            when (action) {
                is SettingsRowAction.ToggleChanged ->
                    when (action.id) {
                        FLOATING_BALL_ROW_ID -> viewModel.sendIntent(
                            GeneralSettingsIntent.ToggleFloatingBall(
                                action.checked
                            )
                        )

                        FLOATING_BALL_AUTO_EXPAND_ROW_ID -> viewModel.sendIntent(
                            GeneralSettingsIntent.ToggleFloatingBallAutoExpand(action.checked)
                        )

                        RESIDENT_NOTIFICATION_ROW_ID -> viewModel.sendIntent(
                            GeneralSettingsIntent.ToggleResidentNotification(
                                action.checked
                            )
                        )

                        TURN_NOTIFICATION_ROW_ID -> viewModel.sendIntent(
                            GeneralSettingsIntent.ToggleTurnNotification(
                                action.checked
                            )
                        )

                        LOAD_LAST_ROW_ID -> viewModel.sendIntent(
                            GeneralSettingsIntent.ToggleLoadLastConversation(
                                action.checked
                            )
                        )

                        ALWAYS_SHOW_ACTIONS_ROW_ID -> viewModel.sendIntent(
                            GeneralSettingsIntent.ToggleAlwaysShowMessageActions(
                                action.checked
                            )
                        )
                    }

                else -> Unit
            }
        },
    )
}
