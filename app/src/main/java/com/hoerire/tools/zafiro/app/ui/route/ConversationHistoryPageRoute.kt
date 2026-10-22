package com.hoerire.tools.zafiro.app.ui.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.PageBackHandler
import com.hoerire.tools.zafiro.app.ui.PageChromeContribution
import com.hoerire.tools.zafiro.app.ui.PageChromeMenuItem
import com.hoerire.tools.zafiro.app.ui.RegisterPageChrome
import com.hoerire.tools.zafiro.app.ui.content.ConversationHistoryPageContent
import com.hoerire.tools.zafiro.app.ui.model.conversation.ConversationHistoryEffect
import com.hoerire.tools.zafiro.app.ui.model.conversation.ConversationHistoryIntent
import com.hoerire.tools.zafiro.app.ui.model.conversation.ConversationHistoryViewModel
import com.hoerire.tools.zafiro.app.ui.nav.TextTitle
import com.hoerire.tools.zafiro.app.ui.nav.TopBarActionSpec

@Composable
internal fun ConversationHistoryPageRoute(
    activeConversationId: String?,
    onBack: () -> Unit,
    onConversationSelected: (String) -> Unit,
    onCurrentConversationDeleted: suspend (String) -> Unit,
    onNewConversation: (() -> Unit)? = null,
) {
    val viewModel = pageViewModel<ConversationHistoryViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnConversationSelected by rememberUpdatedState(onConversationSelected)
    val latestOnCurrentConversationDeleted by rememberUpdatedState(onCurrentConversationDeleted)
    val deleteLabel = stringResource(R.string.ui_conversation_action_delete)
    val pinLabel = stringResource(R.string.ui_conversation_action_pin)
    val unpinLabel = stringResource(R.string.ui_conversation_action_unpin)
    val selectionCountTitle = stringResource(
        R.string.ui_conversation_selection_count,
        uiState.selectedIds.size,
    )

    LaunchedEffect(viewModel) {
        viewModel.sendIntent(ConversationHistoryIntent.Load)
    }
    LaunchedEffect(viewModel, activeConversationId) {
        viewModel.sendIntent(ConversationHistoryIntent.SetActiveConversation(activeConversationId))
    }
    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ConversationHistoryEffect.OpenConversation ->
                    latestOnConversationSelected(effect.id)

                // 活动会话的删除由宿主（ZafiroApp）执行，完成后回报结果，再走与普通删除一致的收尾。
                is ConversationHistoryEffect.DeleteActiveConversation ->
                    runCatching { latestOnCurrentConversationDeleted(effect.id) }
                        .onSuccess {
                            viewModel.sendIntent(
                                ConversationHistoryIntent.ActiveConversationDeleted(effect.id),
                            )
                        }
                        .onFailure { throwable ->
                            viewModel.sendIntent(
                                ConversationHistoryIntent.DeleteFailed(
                                    throwable.message ?: throwable::class.java.simpleName,
                                ),
                            )
                        }
            }
        }
    }

    // 选择态只在列表有内容时进入才有意义；加载 / 出错时不给入口。
    val canEnterSelection =
        uiState.conversations.isNotEmpty() && !uiState.isLoading && uiState.errorMessage == null
    val pageChromeContribution = remember(
        uiState.selecting,
        canEnterSelection,
        selectionCountTitle,
        deleteLabel,
        pinLabel,
        unpinLabel,
    ) {
        if (uiState.selecting) {
            PageChromeContribution(
                titleSpec = TextTitle(selectionCountTitle),
                leftAction = null,
                menuItems = listOf(
                    PageChromeMenuItem(
                        key = "delete",
                        title = deleteLabel,
                        onClick = { viewModel.sendIntent(ConversationHistoryIntent.RequestBatchDelete) },
                    ),
                    PageChromeMenuItem(
                        key = "pin",
                        title = pinLabel,
                        onClick = {
                            viewModel.sendIntent(
                                ConversationHistoryIntent.BatchSetPinned(
                                    true
                                )
                            )
                        },
                    ),
                    PageChromeMenuItem(
                        key = "unpin",
                        title = unpinLabel,
                        onClick = {
                            viewModel.sendIntent(
                                ConversationHistoryIntent.BatchSetPinned(
                                    false
                                )
                            )
                        },
                    ),
                ),
                backHandler = PageBackHandler(
                    shouldConsumeBack = { true },
                    onConsumeBack = { viewModel.sendIntent(ConversationHistoryIntent.ExitSelection) },
                ),
            )
        } else {
            PageChromeContribution(
                leftAction = null,
                rightAction = if (canEnterSelection) {
                    TopBarActionSpec(
                        icon = Icons.Default.Checklist,
                        onClick = { viewModel.sendIntent(ConversationHistoryIntent.EnterSelection) },
                        contentDescription = "Select Button",
                    )
                } else {
                    null
                },

                backHandler = PageBackHandler(
                    shouldConsumeBack = { true },
                    onConsumeBack = { latestOnBack() },
                ),
            )
        }
    }
    RegisterPageChrome(pageChromeContribution)

    Box(modifier = Modifier.fillMaxSize()) {
    ConversationHistoryPageContent(
        uiState = uiState,
        onNewConversation = onNewConversation,
        onConversationClick = { id ->
            viewModel.sendIntent(ConversationHistoryIntent.ClearUnread(id))
            latestOnConversationSelected(id)
        },
        onConversationDelete = { id ->
            viewModel.sendIntent(ConversationHistoryIntent.Delete(id))
        },
        onConversationRename = { id, newTitle ->
            viewModel.sendIntent(ConversationHistoryIntent.Rename(id, newTitle))
        },
        onConversationFork = { id ->
            viewModel.sendIntent(ConversationHistoryIntent.Fork(id))
        },
        onConversationPin = { id, pinned ->
            viewModel.sendIntent(ConversationHistoryIntent.SetPinned(id, pinned))
        },
        onConversationToggleSelection = { id ->
            viewModel.sendIntent(ConversationHistoryIntent.ToggleSelection(id))
        },
        onConfirmBatchDelete = {
            viewModel.sendIntent(ConversationHistoryIntent.ConfirmBatchDelete)
        },
        onDismissBatchDelete = {
            viewModel.sendIntent(ConversationHistoryIntent.DismissBatchDeleteConfirmation)
        },
    )

    }
}
