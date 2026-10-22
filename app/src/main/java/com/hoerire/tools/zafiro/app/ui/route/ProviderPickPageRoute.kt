package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hoerire.tools.uikit.infra.component.SettingsDetailPageDefaults
import com.hoerire.tools.uikit.infra.component.TintLiquidButton
import com.hoerire.tools.uikit.infra.liquidScreenActionButtonBottomPadding
import com.hoerire.tools.uikit.infra.liquidScreenBottomPadding
import com.hoerire.tools.zafiro.app.ui.content.SelectionOption
import com.hoerire.tools.zafiro.app.ui.content.SelectionPageContent
import com.hoerire.tools.zafiro.app.ui.model.ProviderSpecs
import com.hoerire.tools.zafiro.app.ui.nav.ConfigurePage
import com.hoerire.tools.zafiro.app.ui.nav.HomePage
import com.hoerire.tools.zafiro.app.ui.nav.TextTitle
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage
import com.hoerire.tools.zafiro.repo.XRepo
import kotlinx.coroutines.launch

@Composable
internal fun ProviderPickPageRoute(
    onPush: (ZafiroPage) -> Unit,
    onResetTo: (ZafiroPage) -> Unit,
) {
    val scope = rememberCoroutineScope()

    fun skipOnboarding() {
        scope.launch {
            if (!XRepo.onboardingCompleted()) XRepo.setOnboardingCompleted(true)
            onResetTo(HomePage)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SelectionPageContent(
            options = ProviderSpecs.all.map { spec ->
                val colors = providerButtonColorsOrNull(spec)
                SelectionOption(
                    id = spec.id,
                    title = spec.brandName,
                    leadingIconRes = spec.iconRes,
                    tintLeadingIcon = spec.tintIcon,
                    darkContainerColor = colors?.darkContainerColor,
                    lightContainerColor = colors?.lightContainerColor,
                    darkContentColor = colors?.darkContentColor,
                    lightContentColor = colors?.lightContentColor,
                    darkIconColor = colors?.darkIconColor,
                    lightIconColor = colors?.lightIconColor,
                    onClick = {
                        onPush(
                            ConfigurePage(
                                providerId = spec.id,
                                explicitTitleSpec = TextTitle(spec.brandName),
                            ),
                        )
                    },
                )
            },
            contentBottomPadding = liquidScreenBottomPadding() +
                SettingsDetailPageDefaults.RootVerticalSpacing +
                SettingsDetailPageDefaults.ActionButtonReservedHeight,
        )
        TintLiquidButton(
            text = "暂不设置",
            onClick = { skipOnboarding() },
            buttonHeight = SettingsDetailPageDefaults.ActionButtonReservedHeight,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = SettingsDetailPageDefaults.HorizontalPadding,
                    end = SettingsDetailPageDefaults.HorizontalPadding,
                    bottom = liquidScreenActionButtonBottomPadding(),
                ),
        )
    }
}