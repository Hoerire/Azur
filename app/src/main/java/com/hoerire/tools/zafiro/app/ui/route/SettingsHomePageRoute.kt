package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.hoerire.tools.zafiro.app.ui.content.SettingsHomePageContent
import com.hoerire.tools.zafiro.app.ui.nav.PageTitleSpec
import com.hoerire.tools.zafiro.app.ui.nav.SettingsDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroSettingsGroup

@Composable
internal fun SettingsHomePageRoute(
    onPush: (ZafiroPage) -> Unit,
) {
    SettingsHomePageContent(
        onOpenGroup = { group ->
            onPush(
                SettingsDetailPage(
                    group = group,
                    explicitTitleSpec = settingsDetailTitleSpec(group),
                )
            )
        },
    )
}

private fun settingsDetailTitleSpec(group: ZafiroSettingsGroup): PageTitleSpec? {
    return when (group) {
        else -> null
    }
}
