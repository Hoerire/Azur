package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.hoerire.tools.uikit.infra.ConfirmationLiquidDialog
import com.hoerire.tools.uikit.infra.component.SettingsGroupCard
import com.hoerire.tools.uikit.infra.component.SettingsItemDivider
import com.hoerire.tools.uikit.infra.component.SettingsListItem
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.model.ExecutionRuleDeleteConfirmationState
import com.hoerire.tools.zafiro.app.ui.model.ExecutionRulesInlineError
import com.hoerire.tools.zafiro.app.ui.model.ExecutionRulesSettingsEffect
import com.hoerire.tools.zafiro.app.ui.model.ExecutionRulesSettingsIntent
import com.hoerire.tools.zafiro.app.ui.model.ExecutionRulesSettingsUiState
import com.hoerire.tools.zafiro.app.ui.model.ExecutionRulesSettingsViewModel
import com.hoerire.tools.zafiro.app.ui.model.hasUnsavedChanges
import com.hoerire.tools.zafiro.app.ui.nav.ExecutionRuleDetailPage
import com.hoerire.tools.zafiro.settings.model.RuntimeExecutionRuleEnabledMode

@Composable
fun ExecutionRuleDetailContent(
    page: ExecutionRuleDetailPage,
    onBack: () -> Unit,
) {
    val viewModel = pageViewModel<ExecutionRulesSettingsViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    var requestedFocusField by rememberSaveable {
        mutableStateOf<ExecutionRuleEditableField?>(null)
    }

    EditableSettingsDetailChrome(
        isCreating = page.isCreating,
        hasUnsavedChanges = {
            uiState.formState.hasUnsavedChanges
        },
        onDiscardChanges = onBack,
        onDelete = {
            viewModel.sendIntent(ExecutionRulesSettingsIntent.RequestDelete)
        },
        hasDeleteConfirmation = {
            uiState.deleteConfirmation != null
        },
        onDismissDeleteConfirmation = {
            viewModel.sendIntent(ExecutionRulesSettingsIntent.DismissDeleteConfirmation)
        },
    ) {
        ExecutionRuleDetailContentBody(
            uiState = uiState,
            requestedFocusField = requestedFocusField,
            onRequestedFocusHandled = {
                requestedFocusField = null
            },
            onNameChange = { value ->
                viewModel.sendIntent(ExecutionRulesSettingsIntent.NameChanged(value))
            },
            onEnabledModeChange = { value ->
                viewModel.sendIntent(ExecutionRulesSettingsIntent.EnabledModeChanged(value))
            },
            onPatternsInputChange = { value ->
                viewModel.sendIntent(ExecutionRulesSettingsIntent.PatternsChanged(value))
            },
            onToolFilterInputChange = { value ->
                viewModel.sendIntent(ExecutionRulesSettingsIntent.ToolFilterChanged(value))
            },
            onHintInputChange = { value ->
                viewModel.sendIntent(ExecutionRulesSettingsIntent.HintChanged(value))
            },
            onSave = {
                viewModel.sendIntent(ExecutionRulesSettingsIntent.Save)
            },
        )

        ExecutionRuleDeleteConfirmationDialog(
            state = uiState.deleteConfirmation,
            onDismissRequest = {
                viewModel.sendIntent(ExecutionRulesSettingsIntent.DismissDeleteConfirmation)
            },
            onConfirmClick = {
                viewModel.sendIntent(ExecutionRulesSettingsIntent.ConfirmDelete)
            },
        )
    }

    LaunchedEffect(page.routeKey) {
        if (page.isCreating) {
            viewModel.sendIntent(ExecutionRulesSettingsIntent.StartCreate)
        } else {
            viewModel.sendIntent(ExecutionRulesSettingsIntent.Load)
        }
    }

    LaunchedEffect(page.routeKey, uiState.items.size, page.isCreating) {
        if (!page.isCreating && page.ruleIndex in uiState.items.indices) {
            viewModel.sendIntent(ExecutionRulesSettingsIntent.StartEdit(page.ruleIndex))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                ExecutionRulesSettingsEffect.ExitDetail -> onBack()
                ExecutionRulesSettingsEffect.FocusName -> {
                    requestedFocusField = ExecutionRuleEditableField.Name
                }

                ExecutionRulesSettingsEffect.FocusPatterns -> {
                    requestedFocusField = ExecutionRuleEditableField.Patterns
                }
            }
        }
    }
}

private enum class ExecutionRuleEditableField {
    Name,
    Patterns,
    ToolFilter,
    Hint,
}

@Composable
private fun ExecutionRuleDetailContentBody(
    uiState: ExecutionRulesSettingsUiState,
    requestedFocusField: ExecutionRuleEditableField?,
    onRequestedFocusHandled: () -> Unit,
    onNameChange: (String) -> Unit,
    onEnabledModeChange: (RuntimeExecutionRuleEnabledMode) -> Unit,
    onPatternsInputChange: (String) -> Unit,
    onToolFilterInputChange: (String) -> Unit,
    onHintInputChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showEnabledModeDialog by rememberSaveable { mutableStateOf(false) }

    EditableSettingsDetailFormScaffold(
        actionText = stringResource(R.string.execution_rules_save_action),
        requestedFocusField = requestedFocusField,
        onRequestedFocusHandled = onRequestedFocusHandled,
        onActionClick = onSave,
        description = stringResource(R.string.execution_rules_editor_description),
        inlineErrorText = executionRulesInlineErrorText(uiState.inlineError),
        actionEnabled = !uiState.isSaving,
    ) { fieldController ->
        SettingsGroupCard {
            SettingControlledExpandableTextItem(
                field = ExecutionRuleEditableField.Name,
                controller = fieldController,
                title = stringResource(R.string.execution_rules_field_name),
                value = uiState.formState.name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.execution_rules_field_name_hint),
                description = executionRulesFieldErrorText(uiState.formState.nameErrorResId),
                enabled = !uiState.isSaving,
                minLines = 1,
                maxLines = 1,
            )
            SettingsItemDivider()
            ExecutionRuleEnabledModeRow(
                selectedMode = uiState.formState.enabledMode,
                enabled = !uiState.isSaving,
                onClick = {
                    fieldController.clearActiveField()
                    showEnabledModeDialog = true
                },
            )
        }

        SettingsGroupCard {
            SettingControlledExpandableTextItem(
                field = ExecutionRuleEditableField.Patterns,
                controller = fieldController,
                title = stringResource(R.string.execution_rules_field_patterns),
                value = uiState.formState.patternsInput,
                onValueChange = onPatternsInputChange,
                placeholder = stringResource(R.string.execution_rules_field_patterns_hint),
                description = executionRulesFieldErrorText(uiState.formState.patternsErrorResId)
                    ?: stringResource(R.string.execution_rules_field_patterns_description),
                enabled = !uiState.isSaving,
                minLines = 4,
                maxLines = 6,
            )
            SettingsItemDivider()
            SettingControlledExpandableTextItem(
                field = ExecutionRuleEditableField.ToolFilter,
                controller = fieldController,
                title = stringResource(R.string.execution_rules_field_tool_filter),
                value = uiState.formState.toolFilterInput,
                onValueChange = onToolFilterInputChange,
                placeholder = stringResource(R.string.execution_rules_field_tool_filter_hint),
                description = stringResource(R.string.execution_rules_field_tool_filter_description),
                enabled = !uiState.isSaving,
                minLines = 1,
                maxLines = 2,
            )
            SettingsItemDivider()
            SettingControlledExpandableTextItem(
                field = ExecutionRuleEditableField.Hint,
                controller = fieldController,
                title = stringResource(R.string.execution_rules_field_remedy),
                value = uiState.formState.hintInput,
                onValueChange = onHintInputChange,
                placeholder = stringResource(R.string.execution_rules_field_remedy_hint),
                description = stringResource(R.string.execution_rules_field_remedy_description),
                enabled = !uiState.isSaving,
                minLines = 1,
                maxLines = 3,
            )
        }
    }

    // 生效时机：单选弹窗（SplitButton 四选项文本放不下，回归 PRD §3）
    val modeOptions = RuntimeExecutionRuleEnabledMode.entries.map { mode ->
        ExecutionRuleModeOption(mode, stringResource(mode.labelRes()))
    }
    SingleChoiceLiquidDialog(
        visible = showEnabledModeDialog,
        onDismissRequest = { showEnabledModeDialog = false },
        title = stringResource(R.string.execution_rules_field_enabled_mode),
        options = modeOptions,
        selectedId = uiState.formState.enabledMode.name,
        optionId = ExecutionRuleModeOption::id,
        optionLabel = ExecutionRuleModeOption::label,
        onSelect = { option ->
            showEnabledModeDialog = false
            onEnabledModeChange(option.mode)
        },
    )
}

private data class ExecutionRuleModeOption(
    val mode: RuntimeExecutionRuleEnabledMode,
    val label: String,
) {
    val id: String get() = mode.name
}

@Composable
private fun ExecutionRuleEnabledModeRow(
    selectedMode: RuntimeExecutionRuleEnabledMode,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    SettingsListItem(
        title = stringResource(R.string.execution_rules_field_enabled_mode),
        currentState = stringResource(selectedMode.labelRes()),
        showChevron = true,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun executionRulesFieldErrorText(errorResId: Int?): String? {
    return errorResId?.let { stringResource(id = it) }
}

@Composable
private fun executionRulesInlineErrorText(error: ExecutionRulesInlineError?): String? {
    return when (error) {
        null -> null
        is ExecutionRulesInlineError.LoadFailed -> error.message
            ?: stringResource(error.fallbackResId)

        is ExecutionRulesInlineError.SaveFailed -> error.message
            ?: stringResource(error.fallbackResId)

        is ExecutionRulesInlineError.DeleteFailed -> error.message
            ?: stringResource(error.fallbackResId)
    }
}

private fun RuntimeExecutionRuleEnabledMode.labelRes(): Int {
    return when (this) {
        RuntimeExecutionRuleEnabledMode.ALWAYS -> R.string.execution_rules_enabled_mode_enabled
        RuntimeExecutionRuleEnabledMode.LOCKED_ONLY -> R.string.execution_rules_enabled_mode_locked_only
        RuntimeExecutionRuleEnabledMode.DISABLED -> R.string.execution_rules_enabled_mode_disabled
        RuntimeExecutionRuleEnabledMode.CONFIRM -> R.string.execution_rules_enabled_mode_confirm
    }
}

@Composable
private fun ExecutionRuleDeleteConfirmationDialog(
    state: ExecutionRuleDeleteConfirmationState?,
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
) {
    ConfirmationLiquidDialog(
        visible = state != null,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.execution_rules_delete_dialog_title),
        text = stringResource(R.string.execution_rules_delete_dialog_text, state?.value.orEmpty()),
        negativeButtonText = stringResource(R.string.delete_dialog_cancel),
        positiveButtonText = stringResource(R.string.delete_dialog_confirm),
        onNegativeClick = onDismissRequest,
        onPositiveClick = onConfirmClick,
    )
}
