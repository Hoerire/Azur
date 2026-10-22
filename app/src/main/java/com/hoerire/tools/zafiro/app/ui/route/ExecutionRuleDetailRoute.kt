package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.hoerire.tools.zafiro.app.ui.content.ExecutionRuleDetailContent
import com.hoerire.tools.zafiro.app.ui.nav.ExecutionRuleDetailPage

@Composable
internal fun ExecutionRuleDetailRoute(
    page: ExecutionRuleDetailPage,
    onBack: () -> Unit,
) {
    ExecutionRuleDetailContent(
        page = page,
        onBack = onBack,
    )
}
