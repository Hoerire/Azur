package com.hoerire.tools.zafiro.app.ui.model

import com.hoerire.tools.zafiro.app.ui.nav.HomePage
import com.hoerire.tools.zafiro.app.ui.nav.OnboardingThemePage
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage
import com.hoerire.tools.zafiro.repo.XRepo

data class AppLaunchDecision(
    val initialPage: ZafiroPage,
    val onboardingCompleted: Boolean,
    /** BCP-47 tag；空串 = 跟随系统。冷启动期间一次性应用，供 MainActivity 在 setContent 前调用。 */
    val languageTag: String,
) {
    companion object {
        suspend fun resolve(): AppLaunchDecision {
            val onboardingCompleted = XRepo.onboardingCompleted()
            val languageTag = XRepo.languageTag()
            val initialPage = if (onboardingCompleted) {
                HomePage
            } else {
                OnboardingThemePage
            }
            return AppLaunchDecision(
                initialPage = initialPage,
                onboardingCompleted = onboardingCompleted,
                languageTag = languageTag,
            )
        }
    }
}