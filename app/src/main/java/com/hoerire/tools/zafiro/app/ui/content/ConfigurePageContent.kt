package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.hoerire.tools.uikit.infra.ConfirmationLiquidDialog
import com.hoerire.tools.uikit.infra.component.SettingsDetailFormScaffold
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.model.ConfigureInlineError
import com.hoerire.tools.zafiro.app.ui.model.ConfigureUiState
import com.hoerire.tools.zafiro.app.ui.model.EndpointMismatch

@Composable
fun ConfigurePageContent(
    uiState: ConfigureUiState,
    buttonDarkContainerColor: Color = MaterialTheme.colorScheme.primary,
    buttonLightContainerColor: Color = MaterialTheme.colorScheme.primary,
    buttonDarkContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    buttonLightContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onEndpointOverrideChange: (Boolean) -> Unit,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onProtocolSelected: (String) -> Unit = {},
    onThinkingLevelSelected: (String) -> Unit = {},
    onSupportsImagesChange: (Boolean) -> Unit = {},
    onToggleApiKeyVisibility: () -> Unit,
    onProxyChange: (String) -> Unit = {},
    onMaxTokensChange: (String) -> Unit = {},
    onContextWindowChange: (String) -> Unit = {},
    onComplete: () -> Unit,
    onConfirmEndpointMismatch: () -> Unit = {},
    onCancelEndpointMismatch: () -> Unit = {},
    onShowModelCatalogSheet: () -> Unit = {},
    onHideModelCatalogSheet: () -> Unit = {},
    onSelectCatalogModel: (String) -> Unit = {},
    requestedFocusField: ConfigureEditableField? = null,
    onRequestedFocusHandled: () -> Unit = {},
) {
    val policy = onboardingConfigurePolicy(uiState.providerSpec)
    val fieldController = rememberEditableDetailFieldController(
        requestedFocusField = requestedFocusField,
        onRequestedFocusHandled = onRequestedFocusHandled,
    )

    LaunchedEffect(uiState.endpointOverrideEnabled) {
        if (!uiState.endpointOverrideEnabled &&
            fieldController.expandedField == ConfigureEditableField.Endpoint
        ) {
            fieldController.clearActiveField()
        }
    }

    SettingsDetailFormScaffold(
        actionText = stringResource(R.string.ui_onboard_configure_next),
        onActionClick = onComplete,
        description = stringResource(R.string.ui_onboard_configure_description),
        inlineErrorText = configureInlineErrorText(uiState.inlineError),
        actionEnabled = !uiState.isSaving,
        onBackgroundTap = fieldController.clearActiveField,
        actionButtonDarkContainerColor = buttonDarkContainerColor,
        actionButtonLightContainerColor = buttonLightContainerColor,
        actionButtonDarkContentColor = buttonDarkContentColor,
        actionButtonLightContentColor = buttonLightContentColor,
    ) {
        ConfigureIdentitySettingsBlock(
            uiState = uiState,
            showNameField = false,
            fieldController = fieldController,
            onNameChange = {},
            onProxyChange = onProxyChange,
        )

        ConfigureConnectionSettingsBlock(
            uiState = uiState,
            policy = policy,
            fieldController = fieldController,
            onEndpointOverrideChange = onEndpointOverrideChange,
            onEndpointChange = onEndpointChange,
            onModelChange = onModelChange,
            onApiKeyChange = onApiKeyChange,
            onToggleApiKeyVisibility = onToggleApiKeyVisibility,
            onShowModelCatalogSheet = onShowModelCatalogSheet,
        )

        ConfigureProtocolSettingsBlock(
            uiState = uiState,
            fieldController = fieldController,
            onSupportsImagesChange = onSupportsImagesChange,
            onProtocolSelected = onProtocolSelected,
            onThinkingLevelSelected = onThinkingLevelSelected,
            onMaxTokensChange = onMaxTokensChange,
            onContextWindowChange = onContextWindowChange,
        )
    }

    EndpointMismatchDialog(
        mismatch = uiState.pendingEndpointMismatch,
        onConfirm = onConfirmEndpointMismatch,
        onCancel = onCancelEndpointMismatch,
    )

    ModelCatalogSheet(
        visible = uiState.showModelCatalogSheet,
        catalog = uiState.modelCatalog,
        currentModelInput = uiState.modelInput,
        onDismissRequest = onHideModelCatalogSheet,
        onSelect = onSelectCatalogModel,
    )
}

@Composable
internal fun configureInlineErrorText(error: ConfigureInlineError?): String? {
    return when (error) {
        null -> null
        is ConfigureInlineError.LoadFailed -> stringResource(
            R.string.ui_onboard_configure_error_load_failed,
            error.reason.message,
        )

        is ConfigureInlineError.SaveFailed -> stringResource(
            R.string.ui_onboard_configure_error_save_failed,
            error.reason.message,
        )
    }
}

@Composable
internal fun EndpointMismatchDialog(
    mismatch: EndpointMismatch?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (mismatch == null) return
    val isSwitch = mismatch.origin == EndpointMismatch.Origin.SwitchProtocol
    ConfirmationLiquidDialog(
        visible = true,
        onDismissRequest = onCancel,
        title = stringResource(R.string.ui_settings_configure_endpoint_mismatch_title),
        text = stringResource(
            if (isSwitch) {
                R.string.ui_settings_configure_endpoint_mismatch_text_switch
            } else {
                R.string.ui_settings_configure_endpoint_mismatch_text_save
            },
            mismatch.expectedEndpoint,
        ),
        negativeButtonText = stringResource(
            if (isSwitch) {
                R.string.ui_settings_configure_endpoint_mismatch_cancel_keep
            } else {
                R.string.ui_settings_configure_endpoint_mismatch_cancel_save
            },
        ),
        positiveButtonText = stringResource(
            R.string.ui_settings_configure_endpoint_mismatch_confirm_update
        ),
        onNegativeClick = onCancel,
        onPositiveClick = onConfirm,
    )
}

