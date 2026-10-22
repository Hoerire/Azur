package com.hoerire.tools.zafiro.app.ui.model

import android.net.Uri
import androidx.annotation.StringRes
import com.hoerire.tools.uikit.base.ComposeMVIViewModel
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.repo.XRepo

enum class AboutSettingsItemId {
    AuthorHomepage,
    Github,
    Afdian,
    Telegram,
    FeatureFeedback,
    BugFeedback,
}

data class AboutSettingsItemUiState(
    val id: AboutSettingsItemId,
    @StringRes val titleRes: Int,
    val uri: String?,
    @StringRes val bodyTemplateRes: Int? = null,
    val feedbackTitle: String? = null,
) {
    val canOpen: Boolean
        get() = uri != null || bodyTemplateRes != null
}

data class AboutSettingsUiState(
    val items: List<AboutSettingsItemUiState> = emptyList(),
)

sealed interface AboutSettingsIntent {
    data class OpenItem(val id: AboutSettingsItemId) : AboutSettingsIntent
}

sealed interface AboutSettingsEffect {
    data class OpenUri(val uri: String) : AboutSettingsEffect
    data class OpenFeedbackIssue(
        val title: String,
        @StringRes val bodyTemplateRes: Int,
        val languageTag: String,
    ) : AboutSettingsEffect
}

class AboutSettingsViewModel :
    ComposeMVIViewModel<AboutSettingsIntent, AboutSettingsUiState, AboutSettingsEffect>() {

    override fun initUiState(): AboutSettingsUiState {
        return AboutSettingsUiState(items = aboutSettingsItems())
    }

    override suspend fun handleIntent(intent: AboutSettingsIntent) {
        when (intent) {
            is AboutSettingsIntent.OpenItem -> openItem(intent.id)
        }
    }

    private suspend fun openItem(id: AboutSettingsItemId) {
        val item = currentState.items.firstOrNull { it.id == id } ?: return
        when {
            item.uri != null -> sendEffect(AboutSettingsEffect.OpenUri(item.uri))
            item.bodyTemplateRes != null && item.feedbackTitle != null ->
                sendEffect(
                    AboutSettingsEffect.OpenFeedbackIssue(
                        item.feedbackTitle,
                        item.bodyTemplateRes,
                        XRepo.languageTag()
                    )
                )
        }
    }
}

private fun aboutSettingsItems(): List<AboutSettingsItemUiState> {
    return listOf(
        AboutSettingsItemUiState(
            id = AboutSettingsItemId.Github,
            titleRes = R.string.ui_settings_about_github,
            uri = "https://github.com/niki914/zafiro",
        ),
    )
}

internal fun buildIssueUri(title: String, body: String): String {
    return Uri.Builder()
        .scheme("https")
        .authority("github.com")
        .path("/Hoerire/anvil/issues/new")
        .appendQueryParameter("title", title)
        .appendQueryParameter("body", body)
        .build()
        .toString()
}

private const val FEATURE_FEEDBACK_TITLE = "[FEATURE] "
private const val BUG_FEEDBACK_TITLE = "[BUG] "
