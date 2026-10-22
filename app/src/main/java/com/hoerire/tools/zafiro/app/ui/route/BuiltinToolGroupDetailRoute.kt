package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.hoerire.tools.zafiro.app.ui.content.BuiltinToolGroupDetailContent
import com.hoerire.tools.zafiro.app.ui.nav.BuiltinToolGroupDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage

@Composable
internal fun BuiltinToolGroupDetailRoute(
    page: BuiltinToolGroupDetailPage,
    onBack: () -> Unit,
    onPush: (ZafiroPage) -> Unit,
) {
    BuiltinToolGroupDetailContent(
        page = page,
        onBack = onBack,
        onPush = onPush,
    )
}
