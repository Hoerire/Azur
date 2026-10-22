package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.hoerire.tools.uikit.infra.ConfirmationLiquidDialog
import com.hoerire.tools.uikit.infra.component.SettingsGroupCard
import com.hoerire.tools.uikit.infra.component.SettingsItemDivider
import com.hoerire.tools.uikit.infra.component.SettingsListItem
import com.hoerire.tools.uikit.infra.component.SettingsListPageContent
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.PageBackHandler
import com.hoerire.tools.zafiro.app.ui.PageChromeContribution
import com.hoerire.tools.zafiro.app.ui.RegisterPageChrome
import com.hoerire.tools.zafiro.app.ui.model.StorageCategory
import com.hoerire.tools.zafiro.app.ui.model.StorageConfirmationState
import com.hoerire.tools.zafiro.app.ui.model.StorageInlineError
import com.hoerire.tools.zafiro.app.ui.model.StorageSettingsIntent
import com.hoerire.tools.zafiro.app.ui.model.StorageSettingsUiState
import com.hoerire.tools.zafiro.app.ui.model.StorageSettingsViewModel
import com.hoerire.tools.zafiro.app.ui.model.formatStorageBytes

@Composable
fun StorageSettingsContent() {
    val viewModel = pageViewModel<StorageSettingsViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val latestUiState by rememberUpdatedState(uiState)
    val latestViewModel by rememberUpdatedState(viewModel)
    val pageChromeContribution = remember(viewModel) {
        PageChromeContribution(
            backHandler = PageBackHandler(
                shouldConsumeBack = { latestUiState.confirmation != null },
                onConsumeBack = {
                    latestViewModel.sendIntent(StorageSettingsIntent.DismissConfirmation)
                },
            ),
        )
    }
    RegisterPageChrome(pageChromeContribution)

    LaunchedEffect(Unit) {
        viewModel.sendIntent(StorageSettingsIntent.Load)
    }

    StorageSettingsContentBody(
        uiState = uiState,
        onRequestClear = { category ->
            viewModel.sendIntent(StorageSettingsIntent.RequestClear(category))
        },
        onConfirmationDismiss = {
            viewModel.sendIntent(StorageSettingsIntent.DismissConfirmation)
        },
        onConfirmationConfirm = {
            viewModel.sendIntent(StorageSettingsIntent.ConfirmClear)
        },
    )
}

@Composable
private fun StorageSettingsContentBody(
    uiState: StorageSettingsUiState,
    onRequestClear: (StorageCategory) -> Unit,
    onConfirmationDismiss: () -> Unit,
    onConfirmationConfirm: () -> Unit,
) {
    SettingsListPageContent(
        description = stringResource(R.string.ui_settings_storage_description),
    ) {
        SettingsGroupCard {
            if (uiState.isLoading && uiState.categories.isEmpty()) {
                SettingsListItem(
                    title = stringResource(R.string.ui_settings_storage_loading),
                    onClick = null,
                )
            } else {
                uiState.categories.forEachIndexed { index, item ->
                    if (index > 0) {
                        SettingsItemDivider()
                    }
                    SettingsListItem(
                        title = stringResource(item.category.titleRes),
                        summary = stringResource(item.category.descriptionRes),
                        currentState = if (item.isClearing) {
                            "…"
                        } else {
                            formatStorageBytes(item.bytes)
                        },
                        showChevron = true,
                        enabled = !item.isClearing,
                        onClick = {
                            onRequestClear(item.category)
                        },
                    )
                }
            }
        }

        uiState.inlineError?.let { error ->
            StorageInlineErrorText(error = error)
        }
    }

    StorageClearConfirmationDialog(
        state = uiState.confirmation,
        onDismissRequest = onConfirmationDismiss,
        onConfirmClick = onConfirmationConfirm,
    )
}

@Composable
private fun StorageClearConfirmationDialog(
    state: StorageConfirmationState?,
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
) {
    if (state == null) return
    ConfirmationLiquidDialog(
        visible = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.ui_settings_storage_clear_dialog_title),
        text = stringResource(
            R.string.ui_settings_storage_clear_dialog_text,
            stringResource(state.category.titleRes),
        ),
        negativeButtonText = stringResource(R.string.ui_settings_storage_clear_dialog_cancel),
        positiveButtonText = stringResource(R.string.ui_settings_storage_clear_dialog_confirm),
        onNegativeClick = onDismissRequest,
        onPositiveClick = onConfirmClick,
    )
}

@Composable
private fun StorageInlineErrorText(error: StorageInlineError) {
    val message = when (error) {
        is StorageInlineError.LoadFailed -> stringResource(
            R.string.ui_settings_storage_error_load_failed,
            error.message ?: "",
        )

        is StorageInlineError.ClearFailed -> stringResource(
            R.string.ui_settings_storage_error_clear_failed,
            error.message ?: "",
        )
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

