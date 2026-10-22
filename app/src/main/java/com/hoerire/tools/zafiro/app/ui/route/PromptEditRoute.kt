package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.hoerire.tools.zafiro.app.ui.content.PromptEditContent

@Composable
internal fun PromptEditRoute(
    onBack: () -> Unit,
) {
    PromptEditContent(
        onBack = onBack,
    )
}
