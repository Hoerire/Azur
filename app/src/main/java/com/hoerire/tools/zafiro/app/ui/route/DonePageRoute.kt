package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.hoerire.tools.zafiro.app.ui.content.DonePageContent
import com.hoerire.tools.zafiro.app.ui.content.DshSubTab
import com.hoerire.tools.zafiro.app.ui.content.DshSubTabs
import com.hoerire.tools.zafiro.app.ui.nav.HomePage
import com.hoerire.tools.zafiro.app.ui.nav.OnboardingPreferencesPage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage
import com.hoerire.tools.zafiro.repo.XRepo

@Composable
internal fun DonePageRoute(
    onPush: (ZafiroPage) -> Unit,
    onResetTo: (ZafiroPage) -> Unit,
) {
    // 到达本页即视为 onboarding 完成：provider 已在上一页保存并置为 active，
    // 冷启动不该因为用户没点到"进入 Azur"而重新走一遍 onboarding。
    LaunchedEffect(Unit) {
        completeOnboarding()
    }

    DonePageContent(
        onEnterHome = {
            // 引导结束直接落在对话页
            DshSubTabs.current = DshSubTab.Chat
            onResetTo(HomePage)
        },
        onOpenPreferences = {
            onPush(OnboardingPreferencesPage)
        },
    )
}

private suspend fun completeOnboarding() {
    if (XRepo.onboardingCompleted()) {
        return
    }
    XRepo.setOnboardingCompleted(true)
}
