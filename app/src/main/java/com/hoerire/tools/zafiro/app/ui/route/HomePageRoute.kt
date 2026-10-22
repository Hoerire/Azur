package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import com.hoerire.tools.zafiro.app.ui.content.HomePageContent
import com.hoerire.tools.zafiro.app.ui.nav.ConversationHistoryPage
import com.hoerire.tools.zafiro.app.ui.nav.SettingsDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.SettingsHomePage
import com.hoerire.tools.zafiro.app.ui.nav.UsagePanelPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage

@Composable
internal fun HomePageRoute(
    isCurrentPage: Boolean = true,
    onPush: (ZafiroPage) -> Unit,
    onPushFromLeft: (ZafiroPage) -> Unit,
    selectedConversationId: String?,
    onConversationSelectionConsumed: (String) -> Unit,
    onActiveConversationChanged: (String?) -> Unit,
    newConversationRequested: Boolean = false,
    onNewConversationRequestConsumed: () -> Unit = {},
) {
    HomePageContent(
        isCurrentPage = isCurrentPage,
        selectedConversationId = selectedConversationId,
        onConversationSelectionConsumed = onConversationSelectionConsumed,
        onActiveConversationChanged = onActiveConversationChanged,
        newConversationRequested = newConversationRequested,
        onNewConversationRequestConsumed = onNewConversationRequestConsumed,
        onOpenHistory = {
            onPushFromLeft(ConversationHistoryPage)
        },
        onOpenSettings = {
            onPush(SettingsHomePage)
        },
        onOpenUsage = { onPush(UsagePanelPage) },
        onOpenSettingsGroup = { group -> onPush(SettingsDetailPage(group)) },
    )
}
