package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.hoerire.tools.zafiro.app.ui.content.SkillDetailContent
import com.hoerire.tools.zafiro.app.ui.nav.SkillDetailPage

@Composable
internal fun SkillDetailRoute(
    page: SkillDetailPage,
    onBack: () -> Unit,
) {
    SkillDetailContent(
        page = page,
        onBack = onBack,
    )
}
