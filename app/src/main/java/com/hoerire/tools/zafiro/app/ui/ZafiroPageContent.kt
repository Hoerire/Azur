package com.hoerire.tools.zafiro.app.ui

import androidx.compose.runtime.Composable
import com.hoerire.tools.uikit.infra.nav.NavigationEntry
import com.hoerire.tools.zafiro.app.ui.nav.BuiltinToolGroupDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ConfigurePage
import com.hoerire.tools.zafiro.app.ui.nav.ConversationHistoryPage
import com.hoerire.tools.zafiro.app.ui.nav.DonePage
import com.hoerire.tools.zafiro.app.ui.nav.ExecutionRuleDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.HomePage
import com.hoerire.tools.zafiro.app.ui.nav.MarkdownScalePage
import com.hoerire.tools.zafiro.app.ui.nav.OnboardingPreferencesPage
import com.hoerire.tools.zafiro.app.ui.nav.OnboardingThemePage
import com.hoerire.tools.zafiro.app.ui.nav.PromptEditPage
import com.hoerire.tools.zafiro.app.ui.nav.ProviderPickPage
import com.hoerire.tools.zafiro.app.ui.nav.SavedConfigDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.SettingsDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.SettingsHomePage
import com.hoerire.tools.zafiro.app.ui.nav.UsagePanelPage
import com.hoerire.tools.zafiro.app.ui.nav.SettingsProviderPickPage
import com.hoerire.tools.zafiro.app.ui.nav.SkillDetailPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage
import com.hoerire.tools.zafiro.app.ui.route.BuiltinToolGroupDetailRoute
import com.hoerire.tools.zafiro.app.ui.route.ConfigurePageRoute
import com.hoerire.tools.zafiro.app.ui.route.ConversationHistoryPageRoute
import com.hoerire.tools.zafiro.app.ui.route.DonePageRoute
import com.hoerire.tools.zafiro.app.ui.route.ExecutionRuleDetailRoute
import com.hoerire.tools.zafiro.app.ui.route.HomePageRoute
import com.hoerire.tools.zafiro.app.ui.route.MarkdownScalePageRoute
import com.hoerire.tools.zafiro.app.ui.route.OnboardingPreferencesRoute
import com.hoerire.tools.zafiro.app.ui.route.OnboardingThemeRoute
import com.hoerire.tools.zafiro.app.ui.route.PromptEditRoute
import com.hoerire.tools.zafiro.app.ui.route.ProviderPickPageRoute
import com.hoerire.tools.zafiro.app.ui.route.SavedConfigDetailRoute
import com.hoerire.tools.zafiro.app.ui.route.SettingsDetailPageRoute
import com.hoerire.tools.zafiro.app.ui.route.SettingsHomePageRoute
import com.hoerire.tools.zafiro.app.ui.content.UsagePanelContent
import com.hoerire.tools.zafiro.app.ui.route.SettingsProviderPickPageRoute
import com.hoerire.tools.zafiro.app.ui.route.SkillDetailRoute

@Composable
fun ZafiroPageContent(
    entry: NavigationEntry<ZafiroPage>,
    isTopPage: Boolean = true,
    onPush: (ZafiroPage) -> Unit,
    onPushFromLeft: (ZafiroPage) -> Unit,
    onPop: () -> Unit,
    onPopMultiple: (Int) -> Unit,
    onPopToRight: () -> Unit,
    onResetTo: (ZafiroPage) -> Unit,
    selectedConversationId: String?,
    onConversationSelected: (String) -> Unit,
    onConversationSelectionConsumed: (String) -> Unit,
    activeConversationId: String?,
    onActiveConversationChanged: (String?) -> Unit,
    onCurrentConversationDeleted: suspend (String) -> Unit,
    newConversationRequested: Boolean = false,
    onNewConversationRequested: () -> Unit = {},
    onNewConversationRequestConsumed: () -> Unit = {},
) {
    when (val page = entry.page) {

        OnboardingThemePage -> OnboardingThemeRoute(
            onPush = onPush,
        )

        ProviderPickPage -> ProviderPickPageRoute(
            onPush = onPush,
            onResetTo = onResetTo,
        )

        SettingsProviderPickPage -> SettingsProviderPickPageRoute(
            onPush = onPush,
        )


        is ConfigurePage -> ConfigurePageRoute(
            page = page,
            onPush = onPush,
        )

        is SavedConfigDetailPage -> SavedConfigDetailRoute(
            page = page,
            onBack = onPop,
            onPopMultiple = onPopMultiple,
        )

        DonePage -> DonePageRoute(
            onPush = onPush,
            onResetTo = onResetTo,
        )

        OnboardingPreferencesPage -> OnboardingPreferencesRoute(
            onResetTo = onResetTo,
        )

        HomePage -> HomePageRoute(
            newConversationRequested = newConversationRequested,
            onNewConversationRequestConsumed = onNewConversationRequestConsumed,
            isCurrentPage = isTopPage,
            onPush = onPush,
            onPushFromLeft = onPushFromLeft,
            selectedConversationId = selectedConversationId,
            onConversationSelectionConsumed = onConversationSelectionConsumed,
            onActiveConversationChanged = onActiveConversationChanged,
        )

        ConversationHistoryPage -> ConversationHistoryPageRoute(
            activeConversationId = activeConversationId,
            onBack = onPopToRight,
            onConversationSelected = { id ->
                onConversationSelected(id)
                // 直接回首页，避免落回中间页（如设置→技能）
                onResetTo(HomePage)
            },
            onCurrentConversationDeleted = onCurrentConversationDeleted,
            onNewConversation = {
                // 先挂上「新建对话」请求，再清栈回首页；Home 落地后消费该请求
                onNewConversationRequested()
                onResetTo(HomePage)
            },
        )

        SettingsHomePage -> SettingsHomePageRoute(
            onPush = onPush,
        )

        UsagePanelPage -> UsagePanelContent()

        is SettingsDetailPage -> SettingsDetailPageRoute(
            page = page,
            onPush = onPush,
            onBack = onPop,
            onDone = {
                // 设置是 HomePage 的子页签，必须先切回 Settings 再 pop，否则落在对话
                com.hoerire.tools.zafiro.app.ui.content.DshSubTabs.current =
                    com.hoerire.tools.zafiro.app.ui.content.DshSubTab.Settings
                onPop()
            },
        )

        PromptEditPage -> PromptEditRoute(
            onBack = onPop,
        )

        is ExecutionRuleDetailPage -> ExecutionRuleDetailRoute(
            page = page,
            onBack = onPop,
        )

        is SkillDetailPage -> SkillDetailRoute(
            page = page,
            onBack = onPop,
        )

        is BuiltinToolGroupDetailPage -> BuiltinToolGroupDetailRoute(
            page = page,
            onBack = onPop,
            onPush = onPush,
        )

        MarkdownScalePage -> MarkdownScalePageRoute()
    
        else -> Unit
    }
}