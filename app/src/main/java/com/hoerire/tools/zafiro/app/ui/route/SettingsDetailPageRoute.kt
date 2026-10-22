package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.hoerire.tools.zafiro.app.ui.content.SettingsDetailPageContent
import com.hoerire.tools.zafiro.app.ui.nav.SettingsDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage

@Composable
internal fun SettingsDetailPageRoute(
    page: SettingsDetailPage,
    onPush: (ZafiroPage) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    SettingsDetailPageContent(
        group = page.group,
        onPush = onPush,
        onBack = onBack,
        onDone = onDone,
    )
}
