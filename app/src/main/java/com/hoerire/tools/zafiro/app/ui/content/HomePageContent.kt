package com.hoerire.tools.zafiro.app.ui.content

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hoerire.tools.logging.Logger
import com.hoerire.tools.uikit.base.LocalAppDarkTheme
import com.hoerire.tools.uikit.infra.LiquidDialog
import com.hoerire.tools.uikit.infra.LiquidMenuItem
import com.hoerire.tools.uikit.infra.LiquidPopover
import com.hoerire.tools.uikit.infra.LocalHasActiveDialog
import com.hoerire.tools.uikit.infra.LocalScreenBackdrop
import com.hoerire.tools.uikit.infra.PopoverAlignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.hoerire.tools.uikit.infra.LiquidGlassPresets
import com.hoerire.tools.uikit.infra.ReportTitleBarCollapsed
import com.hoerire.tools.uikit.infra.interaction.InteractiveHighlight
import com.hoerire.tools.uikit.infra.interaction.LiquidButtonInteractiveStyle
import com.hoerire.tools.uikit.infra.interaction.applyLiquidInteractiveTransform
import com.hoerire.tools.uikit.infra.component.MaterialTintLiquidButton
import com.hoerire.tools.uikit.infra.component.OptionRow
import com.hoerire.tools.uikit.infra.component.OptionSheet
import com.hoerire.tools.uikit.infra.liquidScreenTopPadding
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.uikit.infra.scrollbar.LiquidOverscrollContainer
import com.hoerire.tools.uikit.infra.scrollbar.LiquidOverscrollController
import com.hoerire.tools.uikit.infra.scrollbar.rememberLiquidOverscrollController
import com.hoerire.tools.zafiro.api.model.ApprovalDecision
import com.hoerire.tools.zafiro.api.model.ApprovalRequest
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.conversation.TurnBranchState
import com.hoerire.tools.zafiro.app.notification.TurnNotificationManager
import com.hoerire.tools.zafiro.app.ui.PageBackHandler
import com.hoerire.tools.zafiro.app.ui.PageChromeContribution
import com.hoerire.tools.zafiro.app.ui.PageChromeMenuItem
import com.hoerire.tools.zafiro.app.ui.RegisterPageChrome
import com.hoerire.tools.zafiro.app.ui.model.ToolPresentation
import com.hoerire.tools.zafiro.app.ui.model.home.ActionSource
import com.hoerire.tools.zafiro.app.ui.model.home.FileAttachReason
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatBlock
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatEffect
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatFile
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatImage
import com.hoerire.tools.zafiro.app.ui.model.conversation.ConversationHistoryIntent
import com.hoerire.tools.zafiro.app.ui.model.conversation.ConversationHistoryViewModel
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatIntent
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatTurn
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatUiState
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatStartupRequests
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatViewModel
import com.hoerire.tools.zafiro.app.ui.model.home.HomeToolState
import com.hoerire.tools.zafiro.app.ui.model.home.MessageActionsDisplay
import com.hoerire.tools.zafiro.app.ui.nav.TopBarActionSpec
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroSettingsGroup
import com.hoerire.tools.zafiro.business.application.ApplicationService
import com.hoerire.tools.zafiro.repo.XRepo
import com.hoerire.tools.zafiro.service.findService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * 冷启动后仅首次进入 Home 时抢焦点弹键盘；进程内不再重复
 * （从设置页/历史页返回不打断用户）。
 */
private var composerAutoFocusDone = false
private const val AUTO_FOCUS_MAX_ATTEMPTS = 20
private const val AUTO_FOCUS_RETRY_INTERVAL_MILLIS = 150L

/** 附件入口的日志 TAG（只在相机失败这类异常路径上用）。 */
private const val ATTACH_LOG_TAG = "zafiro_Attach"

/** 图片多选的张数上限。远低于系统的 getPickImagesMaxLimit()，取一个够用且不炸上下文的数。 */
private const val PHOTO_PICK_MAX_ITEMS = 10

@Composable
fun HomePageContent(
    isCurrentPage: Boolean = true,
    selectedConversationId: String?,
    onConversationSelectionConsumed: (String) -> Unit,
    onActiveConversationChanged: (String?) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenSettingsGroup: (ZafiroSettingsGroup) -> Unit = {},
    newConversationRequested: Boolean = false,
    onNewConversationRequestConsumed: () -> Unit = {},
) {
    val viewModel = pageViewModel<HomeChatViewModel>()
    val chatMenuLabel = stringResource(R.string.dsh_menu_history)
    val settingsMenuLabel = stringResource(R.string.ui_settings_menu_entry)
    val usageMenuLabel = stringResource(R.string.dsh_panel_usage)
    val latestViewModel by rememberUpdatedState(viewModel)
    val latestOnOpenHistory by rememberUpdatedState(onOpenHistory)
    val latestOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val latestOnOpenUsage by rememberUpdatedState(onOpenUsage)
    val latestOnConversationSelectionConsumed by rememberUpdatedState(
        onConversationSelectionConsumed
    )
    val latestOnActiveConversationChanged by rememberUpdatedState(onActiveConversationChanged)
    val uiState by viewModel.uiStateFlow.collectAsState()
    val latestUiState by rememberUpdatedState(uiState)

    // 附件失败提示：一次性 effect（不占状态）。
    // 三条文案按原因分（D22）：解析不出路径 / 没拿到全局文件访问权 / 读不到文件。
    val attachToastContext = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HomeChatEffect.FileAttachFailed -> Toast.makeText(
                    attachToastContext,
                    when (effect.reason) {
                        FileAttachReason.Unresolvable -> R.string.ui_home_attach_unresolvable
                        FileAttachReason.NoPermission -> R.string.ui_home_attach_no_permission
                        FileAttachReason.Unreadable -> R.string.ui_home_attach_unreadable
                    },
                    Toast.LENGTH_SHORT,
                ).show()

                HomeChatEffect.EmptyInputAttempted -> Toast.makeText(
                    attachToastContext,
                    R.string.ui_toast_empty_input,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val alwaysShowActions by XRepo.alwaysShowMessageActionsSetting.collectAsState()
    val actionsDisplay = if (alwaysShowActions) {
        MessageActionsDisplay.Always
    } else {
        MessageActionsDisplay.OnTap
    }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val overscrollController = rememberLiquidOverscrollController()

    // Home 必须显式接管顶栏折叠信号：列表位置会由程序化驱动（自动贴底），且工具结果框
    // 自带滚动子树——两者都是壳层累积量看不见的位移；当向上超滚时，内容推入顶栏区，顶栏需变实体遮挡。
    ReportTitleBarCollapsed {
        listState.firstVisibleItemIndex > 0 ||
            listState.firstVisibleItemScrollOffset > 0 ||
            overscrollController.overscrollOffset.value < 0f
    }

    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var isComposerFocused by remember { mutableStateOf(false) }
    val effectiveImeBottom = if (isComposerFocused) imeBottom else 0.dp
    val isKeyboardActive = imeBottom > 0.dp || (isComposerFocused && uiState.input.isNotEmpty())
    LaunchedEffect(isKeyboardActive) {
        if (isKeyboardActive) {
            viewModel.sendIntent(HomeChatIntent.Warmup)
        }
    }
    // 统一视觉底间距：键盘关闭时与 composer 底距一致，不随 ime 放大——键盘打开时
    // 额外空间全部由 composerBottomPadding 提供，箭头/消息的呼吸空间保持固定
    val composerGap = navigationBottom + 20.dp
    val composerBottomPadding = (effectiveImeBottom + 12.dp).coerceAtLeast(composerGap)
    // composer 实测高度（默认 68dp = LiquidChatComposer 的 minHeight），首帧布局后回填
    val composerHeight = remember { mutableStateOf(68.dp) }
    val bottomThresholdPx = with(density) { 24.dp.roundToPx() }
    val lastTurn = uiState.turns.lastOrNull()
    // 贴底由「滚动位置 + contentPadding」共同决定：composer 几何（ime 动画、多行输入
    // 长高）变化时 padding 跟着变，也必须重新贴底，否则最后一条消息被 composer 遮住
    val bottomContentVersion = remember(
        uiState.turns.size,
        uiState.conversationVersion,
        lastTurn?.id,
        lastTurn?.blocks?.size,
        composerBottomPadding,
        composerHeight.value,
    ) {
        listOf(
            uiState.turns.size,
            uiState.conversationVersion,
            lastTurn?.id,
            lastTurn?.blocks?.size,
            composerBottomPadding,
            composerHeight.value,
        )
    }
    val isAtBottom by remember(listState, bottomThresholdPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem =
                layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            val viewportEnd = layoutInfo.viewportEndOffset
            lastVisibleItem.index == layoutInfo.totalItemsCount - 1 &&
                    lastVisibleItem.offset + lastVisibleItem.size <= viewportEnd + bottomThresholdPx
        }
    }
    val shouldFollowBottomState = rememberScrollFollowState(
        interactionSource = listState.interactionSource,
        isScrollInProgress = { listState.isScrollInProgress },
        isAtEnd = { isAtBottom },
    )
    var shouldFollowBottom by shouldFollowBottomState
    // 运行中的块会长高（终端块 320dp）：长高瞬间「是否贴底」就变 false，
    // 500ms 的贴底循环会自己把自己关掉、内容被顶到屏幕外。这里加一道闩：
    // 只有用户真的手动拖过对话才解除跟随，拖回底部再恢复。
    var followLatch by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followLatch = false
        }
    }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) followLatch = true
    }
    val dismissInputFocus = remember(focusManager, keyboardController) {
        {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // 只在内容事件（bottomContentVersion）变化时跳转：恢复跟随本身不触发滚动，
    // 避免「小幅上滚停在阈值内 → 恢复跟随 → 无内容也被拉回底部」
    LaunchedEffect(bottomContentVersion) {
        if (shouldFollowBottom) {
            listState.scrollToItem(uiState.turns.size)
        }
    }
    // 工具实时输出不会改变 bottomContentVersion：运行中额外按 500ms 节拍贴底
    val hasRunningTool = uiState.turns.any { turn ->
        turn.blocks.any { it is HomeChatBlock.Tool && it.status.state == HomeToolState.Running }
    }
    LaunchedEffect(hasRunningTool) {
        val last = uiState.turns.lastIndex
        if (last >= 0) { runCatching { listState.scrollToItem(last); listState.scrollBy(1_000_000f) } }
        var grace = 10
        while (grace-- > 0) {
            if (hasRunningTool) grace = 10
            delay(500)
            if (followLatch) {
                val l = uiState.turns.lastIndex
                if (l >= 0) runCatching { listState.scrollToItem(l); listState.scrollBy(1_000_000f) }
            }
        }
    }
    LaunchedEffect(newConversationRequested) {
        if (newConversationRequested) {
            DshSubTabs.current = DshSubTab.Chat
            onNewConversationRequestConsumed()
            // 正常路径由 HomeChatViewModel.restoreStartupState() 的 finally 消费（新 VM）。
            // 若 VM 已存在且恢复早已跑完，标记会残留 → 这里兜底消费，否则「新建对话」不生效。
            withFrameNanos { }
            kotlinx.coroutines.delay(250)
            if (HomeChatStartupRequests.consumeNewConversation()) {
                latestViewModel.sendIntent(HomeChatIntent.NewConversation)
            }
        }
    }
    LaunchedEffect(selectedConversationId) {
        val id = selectedConversationId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        latestViewModel.sendIntent(HomeChatIntent.LoadConversation(id))
        latestOnConversationSelectionConsumed(id)
        TurnNotificationManager.cancelNotification(id)
    }
    LaunchedEffect(uiState.currentConversationId) {
        latestOnActiveConversationChanged(uiState.currentConversationId)
    }
    DisposableEffect(isCurrentPage, uiState.currentConversationId) {
        findService<ApplicationService>()?.setHomeChatActive(isCurrentPage)
        TurnNotificationManager.visibleConversationId =
            if (isCurrentPage) uiState.currentConversationId else null
        onDispose {
            findService<ApplicationService>()?.setHomeChatActive(false)
            if (TurnNotificationManager.visibleConversationId == uiState.currentConversationId) {
                TurnNotificationManager.visibleConversationId = null
            }
        }
    }
    LaunchedEffect(isCurrentPage, uiState.currentConversationId, uiState.isGenerating) {
        if (isCurrentPage && !uiState.isGenerating) {
            val id = uiState.currentConversationId
            if (!id.isNullOrBlank()) {
                latestViewModel.sendIntent(HomeChatIntent.ClearUnread(id))
            }
        }
    }

    LaunchedEffect(Unit) {
        latestViewModel.sendIntent(HomeChatIntent.RefreshConfigs)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (latestUiState.isConfigMenuVisible) {
                latestViewModel.sendIntent(HomeChatIntent.DismissConfigMenu)
            }
        }
    }

    val rectSaver = remember {
        Saver<Rect?, List<Float>>(
            save = { rect -> rect?.let { listOf(it.left, it.top, it.right, it.bottom) } },
            restore = { list -> Rect(list[0], list[1], list[2], list[3]) },
        )
    }
    var titleAnchorBounds by rememberSaveable(stateSaver = rectSaver) {
        mutableStateOf<Rect?>(null)
    }

    val titleContent: @Composable () -> Unit = remember {
        {
            HomeTitleConfigChip(
                uiStateProvider = { latestUiState },
                onChipClick = {
                    latestViewModel.sendIntent(HomeChatIntent.OpenConfigMenu)
                },
                onAnchorBoundsChanged = { bounds ->
                    titleAnchorBounds = bounds
                },
            )
        }
    }

    var inPlaceSettingsGroup by remember { mutableStateOf<ZafiroSettingsGroup?>(null) }

    val pageChromeContribution = remember(
        settingsMenuLabel,
        titleContent,
        uiState.isConfigMenuVisible,
    ) {
        PageChromeContribution(
            titleContent = titleContent,
            isTitleOccluded = uiState.isConfigMenuVisible,
            actionsVisible = !uiState.isConfigMenuVisible,
            leftAction = TopBarActionSpec(
                icon = Icons.Default.History,
                onClick = {
                    if (latestUiState.isConfigMenuVisible) {
                        latestViewModel.sendIntent(HomeChatIntent.DismissConfigMenu)
                    }
                    latestOnOpenHistory()
                },
                contentDescription = "History Button",
            ),
            backHandler = PageBackHandler(
                shouldConsumeBack = {
                    inPlaceSettingsGroup != null || DshSubTabs.current != DshSubTab.Chat
                },
                onConsumeBack = {
                    if (inPlaceSettingsGroup != null) {
                        inPlaceSettingsGroup = null
                    } else {
                        DshSubTabs.current = DshSubTab.Chat
                    }
                },
            ),
            barItems = listOf(
                PageChromeMenuItem(
                    key = "chat",
                    title = chatMenuLabel,
                    onClick = {
                        if (latestUiState.isConfigMenuVisible) {
                            latestViewModel.sendIntent(HomeChatIntent.DismissConfigMenu)
                        }
                        // 再点一次回对话（与用量/设置同语义）
                        DshSubTabs.toggle(DshSubTab.History)
                    },
                ),
                PageChromeMenuItem(
                    key = "usage",
                    title = usageMenuLabel,
                    onClick = {
                        if (latestUiState.isConfigMenuVisible) {
                            latestViewModel.sendIntent(HomeChatIntent.DismissConfigMenu)
                        }
                        DshSubTabs.toggle(DshSubTab.Usage)
                    },
                ),
                PageChromeMenuItem(
                    key = "settings",
                    title = settingsMenuLabel,
                    onClick = {
                        if (latestUiState.isConfigMenuVisible) {
                            latestViewModel.sendIntent(HomeChatIntent.DismissConfigMenu)
                        }
                        DshSubTabs.toggle(DshSubTab.Settings)
                    },
                ),
            ),
        )
    }
    val composerFocusRequester = remember { FocusRequester() }

    RegisterPageChrome(pageChromeContribution)

    if (DshSubTabs.current == DshSubTab.Settings) {
        SettingsHomePageContent(
            onOpenGroup = { group -> onOpenSettingsGroup(group) },
        )
        return
    }

    if (DshSubTabs.current == DshSubTab.History) {
        HistoryPanelInPlace(
            onConversationSelected = { id ->
                latestViewModel.sendIntent(HomeChatIntent.LoadConversation(id))
                DshSubTabs.current = DshSubTab.Chat
            },
            onNewConversation = {
                latestViewModel.sendIntent(HomeChatIntent.NewConversation)
                DshSubTabs.current = DshSubTab.Chat
            },
        )
        return
    }

    if (DshSubTabs.current == DshSubTab.Usage) {
        UsagePanelContent()
        return
    }

    val configs = remember(uiState.savedConfigs, uiState.activeConfigId) {
        val activeId = uiState.activeConfigId
        if (activeId.isNullOrBlank()) {
            uiState.savedConfigs
        } else {
            val (active, rest) = uiState.savedConfigs.partition { it.id == activeId }
            active + rest
        }
    }

    LiquidPopover(
        expanded = uiState.isConfigMenuVisible,
        onDismissRequest = {
            viewModel.sendIntent(HomeChatIntent.DismissConfigMenu)
        },
        anchorBounds = titleAnchorBounds,
        alignment = PopoverAlignment.BottomCenter,
        scrollable = configs.size > 5,
    ) {
        configs.forEach { config ->
            LiquidMenuItem(
                title = config.name,
                trailingText = config.modelId.takeIf { it != config.name },
                checked = config.id == uiState.activeConfigId,
                onClick = {
                    viewModel.sendIntent(HomeChatIntent.SelectConfig(config.id))
                },
            )
        }
    }

    val hasActiveDialog = LocalHasActiveDialog.current
    // 冷启动键盘焦点：仅进程内首次进入 Home、无草稿输入、不在加载中且当前无弹窗时抢焦点。
    // 标志在首次 effect 执行后即置位：无论聚焦成功、attempts 耗尽还是条件不满足
    // （有草稿/正在生成），都不再重试 —— 否则回答完成时 isGenerating 翻转会重启
    // 本 effect，导致"回答完成后自动弹键盘"（冷启动时未成功聚焦过的场景）。
    if (!composerAutoFocusDone && !uiState.isLoadingConversation && !hasActiveDialog) {
        LaunchedEffect(uiState.input.isBlank(), uiState.isGenerating) {
            if (uiState.input.isBlank() && !uiState.isGenerating) {
                repeat(AUTO_FOCUS_MAX_ATTEMPTS) {
                    delay(AUTO_FOCUS_RETRY_INTERVAL_MILLIS)
                    val focused = runCatching { composerFocusRequester.requestFocus() }.isSuccess
                    if (focused) {
                        keyboardController?.show()
                        composerAutoFocusDone = true
                        return@LaunchedEffect
                    }
                }
            }
            composerAutoFocusDone = true
        }
    }

    HomePageContentBody(
        uiState = uiState,
        actionsDisplay = actionsDisplay,
        listState = listState,
        overscrollController = overscrollController,
        composerBottomPadding = composerBottomPadding,
        composerGap = composerGap,
        composerHeight = composerHeight,
        composerFocusRequester = composerFocusRequester,
        followBottom = shouldFollowBottomState,
        isAtBottom = isAtBottom,
        onContentTap = dismissInputFocus,
        onInputChange = { value ->
            viewModel.sendIntent(HomeChatIntent.InputChanged(value))
        },
        onSendClick = {
            val query = uiState.input.trim()
            val hasAttachments =
                uiState.pendingImages.isNotEmpty() || uiState.pendingFiles.isNotEmpty()
            if (query.isNotEmpty() || hasAttachments) {
                dismissInputFocus()
                shouldFollowBottom = true
            }
            viewModel.sendIntent(HomeChatIntent.Send)
        },
        onStopClick = {
            viewModel.sendIntent(HomeChatIntent.StopGenerating)
        },
        pendingImages = uiState.pendingImages,
        pendingFiles = uiState.pendingFiles,
        onImageAttached = { uri ->
            viewModel.sendIntent(HomeChatIntent.ImageAttached(uri))
        },
        onRemoveImage = { id ->
            viewModel.sendIntent(HomeChatIntent.ImageRemoved(id))
        },
        onCameraCaptured = { uri, path ->
            viewModel.sendIntent(HomeChatIntent.CameraCaptured(uri, path))
        },
        onFileAttached = { uri ->
            viewModel.sendIntent(HomeChatIntent.FileAttached(uri))
        },
        onRemoveFile = { id ->
            viewModel.sendIntent(HomeChatIntent.FileRemoved(id))
        },
        onComposerFocusChanged = { focused ->
            isComposerFocused = focused
        },
        onReGenerate = { id ->
            viewModel.sendIntent(HomeChatIntent.ReGenerateAt(id))
        },
        onFork = { id ->
            viewModel.sendIntent(HomeChatIntent.ForkAt(id))
        },
        onRewind = { id ->
            viewModel.sendIntent(HomeChatIntent.RewindAt(id))
        },
        expandedToolRuns = uiState.expandedToolRuns,
        expandedToolResults = uiState.expandedToolResults,
        expandedThinking = uiState.expandedThinking,
        onToggleToolRun = { turnId, runStartIndex ->
            viewModel.sendIntent(HomeChatIntent.ToggleToolRun(turnId, runStartIndex))
        },
        onToggleToolResult = { turnId, runStartIndex, toolIndex ->
            viewModel.sendIntent(HomeChatIntent.ToggleToolResult(turnId, runStartIndex, toolIndex))
        },
        onToggleThinking = { turnId, blockIndex ->
            viewModel.sendIntent(HomeChatIntent.ToggleThinking(turnId, blockIndex))
        },
        expandedActionTurnId = uiState.expandedActionTurnId,
        expandedActionSource = uiState.expandedActionSource,
        activeThinkingKey = uiState.activeThinkingKey,
        onToggleActionRow = { turnId, source ->
            viewModel.sendIntent(
                HomeChatIntent.ToggleActionRow(turnId, source)
            )
        },
        turnBranches = uiState.turnBranches,
        onSwitchTurnBranch = { turnId, delta ->
            viewModel.sendIntent(HomeChatIntent.SwitchTurnBranch(turnId, delta))
        },
    )

    when (val request = uiState.pendingApproval) {
        is ApprovalRequest.ToolExecution -> {
            ToolPermissionDialog(
                request = request,
                onAllow = { viewModel.sendIntent(HomeChatIntent.ResolveApproval(ApprovalDecision.Allow)) },
                onDeny = { viewModel.sendIntent(HomeChatIntent.ResolveApproval(ApprovalDecision.Deny)) },
            )
        }

        is ApprovalRequest.ScreenControlConsent -> {
            ScreenControlConsentDialog(
                onAgree = { viewModel.sendIntent(HomeChatIntent.ResolveApproval(ApprovalDecision.Allow)) },
                onDeny = { viewModel.sendIntent(HomeChatIntent.ResolveApproval(ApprovalDecision.Deny)) },
            )
        }

        null -> {}
    }
}

/**
 * 屏幕控制知情同意对话框（无障碍 + 悬浮窗）。
 */
@Composable
private fun ScreenControlConsentDialog(
    onAgree: () -> Unit,
    onDeny: () -> Unit,
) {
    LiquidDialog(
        visible = true,
        onDismissRequest = onDeny,
        dismissOnBackgroundTap = false,
        title = {
            Text(
                text = stringResource(R.string.screen_control_consent_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                text = stringResource(R.string.screen_control_consent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            MaterialTintLiquidButton(
                text = stringResource(R.string.screen_control_consent_deny),
                onClick = onDeny,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            MaterialTintLiquidButton(
                text = stringResource(R.string.screen_control_consent_agree),
                onClick = onAgree,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    )
}

/**
 * CONFIRM 型执行规则的用户确认对话框（永不超时，PRD §3）。
 */
@Composable
private fun ToolPermissionDialog(
    request: ApprovalRequest.ToolExecution,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    LiquidDialog(
        visible = true,
        onDismissRequest = onDeny,
        dismissOnBackgroundTap = false,
        title = {
            Text(
                text = stringResource(R.string.tool_permission_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.tool_permission_request_intro, request.toolName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = request.command,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(10.dp),
                )
                Text(
                    text = stringResource(
                        R.string.tool_permission_matched_rule,
                        request.ruleName,
                        request.matchedPattern,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        actions = {
            MaterialTintLiquidButton(
                text = stringResource(R.string.tool_permission_deny),
                onClick = onDeny,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            MaterialTintLiquidButton(
                text = stringResource(R.string.tool_permission_allow),
                onClick = onAllow,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomePageContentBody(
    uiState: HomeChatUiState,
    actionsDisplay: MessageActionsDisplay,
    listState: LazyListState,
    overscrollController: LiquidOverscrollController = rememberLiquidOverscrollController(),
    composerBottomPadding: Dp,
    composerGap: Dp,
    composerHeight: MutableState<Dp>,
    composerFocusRequester: FocusRequester,
    followBottom: MutableState<Boolean>,
    isAtBottom: Boolean,
    onContentTap: () -> Unit,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    pendingImages: List<HomeChatImage>,
    pendingFiles: List<HomeChatFile>,
    onImageAttached: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onCameraCaptured: (String, String) -> Unit,
    onFileAttached: (String) -> Unit,
    onRemoveFile: (String) -> Unit,
    onComposerFocusChanged: (Boolean) -> Unit,
    onReGenerate: (Long) -> Unit,
    onFork: (Long) -> Unit,
    onRewind: (Long) -> Unit,
    expandedToolRuns: Set<String>,
    expandedToolResults: Set<String>,
    expandedThinking: Set<String>,
    onToggleToolRun: (Long, Int) -> Unit,
    onToggleToolResult: (Long, Int, Int) -> Unit,
    onToggleThinking: (Long, Int) -> Unit,
    expandedActionTurnId: Long?,
    expandedActionSource: ActionSource?,
    activeThinkingKey: String? = null,
    onToggleActionRow: (Long, ActionSource) -> Unit,
    turnBranches: Map<Long, TurnBranchState> = emptyMap(),
    onSwitchTurnBranch: (Long, Int) -> Unit = { _, _ -> },
) {

    // 底部避让总高：composer 底距 + 实测高度 + 统一视觉间距。列表贴底留白与箭头
    // 位置同源；键盘关闭时（composerBottomPadding == composerGap）即为
    // composerBottomPadding*2 + composerHeight，composer 顶上方留一个视觉间距
    val bottomClearance = composerBottomPadding + composerHeight.value + composerGap
    val density = LocalDensity.current
    val chatBackdrop = rememberLayerBackdrop()

    // 附件入口：加号 → 选项单（Photos / Camera / File / Folder）。
    val context = LocalContext.current
    var attachSheetVisible by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<CameraTarget?>(null) }

    // 系统图片选择器（photo picker，无权限）：可多选，选完逐张 ingest 落盘 → pendingImages
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(PHOTO_PICK_MAX_ITEMS),
    ) { uris ->
        uris.forEach { onImageAttached(it.toString()) }
    }

    // SAF 文档 / 目录选择器：可多选；把 content uri 原样交给 VM，
    // 由 business:files 逐个解析路径 + 要全局文件访问权，这里不做判断
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            // 图片走图片管线（不需要路径、不需要存储权限），其余仍走文件引用
            val type = context.contentResolver.getType(uri)
            val asImage = isImportableImage(context, uri, type)
            Logger.i(ATTACH_LOG_TAG, "file picked uri=$uri type=$type asImage=$asImage")
            if (asImage) onImageAttached(uri.toString()) else onFileAttached(uri.toString())
        }
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { onFileAttached(it.toString()) }
    }

    // 系统相机：输出目标是我们自己 cache 里的临时文件（FileProvider uri）。
    // 删那个临时文件由 VM 在 ingest 完成之后做，这里不能当场删（原文件还没被拷走）。
    //
    // 为什么不用 MediaStore：官方 TakePicture contract 只塞 EXTRA_OUTPUT，本就不带
    // URI 授权；补上 clipData + grant 之后依然不行——我们刚 insert 的记录是
    // IS_PENDING=1，pending 项对**非所有者**是 owner-only（MediaProvider 自己拦，
    // URI 授权也压不过），相机是另一个 uid，写不进去。真机实测：相机直接回 NOT_OK。
    // FileProvider 的 uri 落在我们自己的目录上，授权就是唯一的门槛，也是官方文档的配方。
    val takePicture = rememberLauncherForActivityResult(
        contract = GrantingTakePicture(),
    ) { result ->
        val target = pendingCapture
        pendingCapture = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            onCameraCaptured(target.uri.toString(), target.file.path)
        } else {
            // 相机没写成（用户取消 / 相机拒绝）：清掉临时文件
            Logger.w(ATTACH_LOG_TAG, "camera result not ok resultCode=${result.resultCode}")
            target?.file?.let { runCatching { it.delete() } }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LiquidOverscrollContainer(
            overscrollController = overscrollController,
            modifier = Modifier.fillMaxSize(),
        ) {
            // 工具调用一开始就挂采集，不等块滚进视野（§9.6：晚订阅 5 秒会丢开头）
            preloadLiveTerminals(uiState.turns)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(chatBackdrop)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onContentTap,
                    ),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = liquidScreenTopPadding(24.dp),
                    end = 20.dp,
                    bottom = bottomClearance,
                ),
            ) {
                itemsIndexed(
                    items = uiState.turns,
                    key = { _, turn -> turn.id },
                ) { index, turn ->
                    // User 气泡组内位置（渲染层：两个相邻 UserBubble 之间无任何内容即同组，见 userBubblePosition）
                    val position = userBubblePosition(uiState.turns, index)
                    // 组归一化：点组内任意一条都弹/聚合到组尾（操作行在组尾 turn 下渲染）
                    val tailIndex = userGroupTailIndex(uiState.turns, index)
                    val tailTurn = uiState.turns[tailIndex]
                    // 顶距：组中/组末用组内间隙与上一气泡连体；组首/单条维持 turn 分隔；分割线紧凑居中
                    val turnTopPad = when {
                        index == 0 -> Modifier
                        turn.isCompactedDivider -> Modifier.padding(top = 8.dp, bottom = 8.dp)
                        position == UserBubblePosition.GroupMid || position == UserBubblePosition.GroupLast ->
                            Modifier.padding(top = UserBubbleGap)

                        else -> Modifier.padding(top = TurnSeparator)
                    }
                    HomeChatTurnItem(
                        turn = turn,
                        userBubblePosition = position,
                        isLastTurn = index == uiState.turns.lastIndex,
                        actionsDisplay = actionsDisplay,
                        userGroupTailTurnId = tailTurn.id,
                        userGroupTappable = tailTurn.blocks.isNotEmpty() || tailIndex == uiState.turns.lastIndex,
                        userGroupText = userGroupText(uiState.turns, index),
                        onContentTap = onContentTap,
                        onReGenerate = onReGenerate,
                        onFork = onFork,
                        onRewind = onRewind,
                        expandedToolRuns = expandedToolRuns,
                        expandedToolResults = expandedToolResults,
                        expandedThinking = expandedThinking,
                        onToggleToolRun = onToggleToolRun,
                        onToggleToolResult = onToggleToolResult,
                        onToggleThinking = onToggleThinking,
                        expandedActionTurnId = expandedActionTurnId,
                        expandedActionSource = expandedActionSource,
                        activeThinkingKey = activeThinkingKey,
                        onToggleActionRow = onToggleActionRow,
                        isGenerating = uiState.isGenerating,
                        elapsedMs = uiState.turnDurations[turn.id],
                        elapsedStartedAtMs = if (uiState.isGenerating && index == uiState.turns.lastIndex) uiState.runningTurnStartedAtMs else null,
                        branchIndex = turnBranches[turn.id]?.current ?: 1,
                        branchCount = turnBranches[turn.id]?.total ?: 1,
                        onSwitchBranch = { delta -> onSwitchTurnBranch(turn.id, delta) },
                        turnOrdinal = uiState.turns.take(index)
                            .count { !it.isCompactedDivider } + 1,
                        // 会话级用量只挂末轮，左边缘与「第N轮」对齐
                        usageText = if (index == uiState.turns.lastIndex &&
                            uiState.contextWindowTokens > 0L
                        ) {
                            val used = uiState.contextUsedTokens
                            // 时段：北京时间 00:30–08:30 为低谷，其余高峰（与用量页同一判定）
                            val peak = DeepSeekPeakScheduleRepo.current().isPeak(
                                java.time.Instant.ofEpochMilli(System.currentTimeMillis()),
                                java.time.ZoneId.of("Asia/Shanghai"),
                            )
                            // 地板价 = 会话首个请求的 prompt（系统提示/工具/技能 + 首条用户消息），
                            // 拆开后新会话不会看起来已经烧了二十几 K
                            val base = uiState.contextBaselineTokens
                            val split = if (base > 0L && base < used) {
                                String.format(
                                    java.util.Locale.US,
                                    "（基础 %.1fk + 本会话 %.1fk）",
                                    base / 1000.0,
                                    (used - base) / 1000.0,
                                )
                            } else {
                                ""
                            }
                            String.format(
                                java.util.Locale.US,
                                "对话用量 %.1fk%s | %.0fk · %.1f%% · %s",
                                used / 1000.0,
                                split,
                                uiState.contextWindowTokens / 1000.0,
                                used * 100.0 / uiState.contextWindowTokens,
                                if (peak) "高峰期" else "低谷期",
                            )
                        } else {
                            null
                        },
                        modifier = turnTopPad.fillMaxWidth(),
                    )
                }
                item(key = "bottom_anchor") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp),
                    )
                }
            }
        }

        // 待发附件条：图片与文件**同一行**（两种卡同尺寸同形状），宽 = composer 本体，
        // 位于 composer 上方 8dp；声明在 composer 之前，万一重合 composer 层级更高盖住它们。
        // composer 拉长/被 IME 顶起时随 composerHeight/composerBottomPadding 精确跟随。
        // 不可点击（TODO: 后续接入点开大图 / 打开文件）
        if (pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()) {
            HomeChatAttachmentRow(
                images = pendingImages,
                files = pendingFiles,
                cardSize = 60.dp,
                cornerRadius = 18.dp,
                onRemoveImage = onRemoveImage,
                onRemoveFile = onRemoveFile,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .padding(bottom = composerBottomPadding + composerHeight.value + 8.dp),
            )
        }


        CompositionLocalProvider(
            LocalScreenBackdrop provides chatBackdrop,
        ) {
            LiquidChatComposer(
                value = uiState.input,
                onValueChange = onInputChange,
                onSendClick = onSendClick,
                onStopClick = onStopClick,
                isGenerating = uiState.isGenerating,
                contextProgress = uiState.contextProgress,
                contextUsedTokens = uiState.contextUsedTokens,
                contextWindowTokens = uiState.contextWindowTokens,
                pendingImages = pendingImages,
                pendingFiles = pendingFiles,
                onAttachImageClick = { attachSheetVisible = true },
                backdrop = chatBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onFocusChanged { focusState ->
                        onComposerFocusChanged(focusState.hasFocus)
                    }
                    .focusRequester(composerFocusRequester)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = composerBottomPadding,
                    )
                    // 放在 padding 之后：只测 composer 本体高度，不含底边距
                    .onSizeChanged { size ->
                        composerHeight.value = with(density) { size.height.toDp() }
                    },
            )
        }

        // 解除贴底锚定且不在底部时出现：点击恢复跟随并平滑滚回底部
        val scrollToBottomScope = rememberCoroutineScope()
        val scrollToBottomInteractiveHighlight = remember(scrollToBottomScope) {
            InteractiveHighlight(animationScope = scrollToBottomScope)
        }
        val isDarkTheme = LocalAppDarkTheme.current
        val scrollToBottomShape = CircleShape
        val scrollToBottomSurfaceColor = LiquidGlassPresets.ScrollToBottom.surfaceColor(isDarkTheme, MaterialTheme.colorScheme)

        AnimatedVisibility(
            visible = !followBottom.value && !isAtBottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomClearance),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(100)),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .drawBackdrop(
                        backdrop = chatBackdrop,
                        shape = { scrollToBottomShape },
                        highlight = { LiquidGlassPresets.ScrollToBottom.highlight(isDarkTheme) },
                        shadow = { LiquidGlassPresets.ScrollToBottom.shadow(isDarkTheme) },
                        effects = {
                            LiquidGlassPresets.ScrollToBottom.applyEffects(this, isDarkTheme)
                        },
                        layerBlock = {
                            applyLiquidInteractiveTransform(
                                style = LiquidButtonInteractiveStyle,
                                pressProgress = scrollToBottomInteractiveHighlight.pressProgress,
                                offset = scrollToBottomInteractiveHighlight.offset,
                                size = size,
                            )
                        },
                        onDrawSurface = {
                            drawRect(scrollToBottomSurfaceColor)
                        },
                    )
                    .clip(scrollToBottomShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        followBottom.value = true
                        scrollToBottomScope.launch {
                            listState.animateScrollToItem(uiState.turns.size)
                        }
                    }
                    .then(scrollToBottomInteractiveHighlight.gestureModifier)
                    .then(
                        if (LiquidButtonInteractiveStyle.highlightEnabled) {
                            scrollToBottomInteractiveHighlight.modifier
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = "Scroll To Bottom Icon",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (uiState.isLoadingConversation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        OptionSheet(
            visible = attachSheetVisible,
            onDismissRequest = { attachSheetVisible = false },
            title = stringResource(R.string.ui_home_attach_sheet_title),
        ) { dismissThen ->
            OptionRow(
                title = stringResource(R.string.ui_home_attach_photos),
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = {
                    dismissThen {
                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                },
            )
            // 相机项：不再有版本门。旧门存在的理由是「MediaStore 的 RELATIVE_PATH/IS_PENDING
            // 要 29+」，改用 FileProvider 之后这个前提没了，26+ 都能跑。
            OptionRow(
                title = stringResource(R.string.ui_home_attach_camera),
                leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                onClick = {
                    dismissThen {
                        val target = createCameraTarget(context)
                        if (target == null) {
                            Logger.w(ATTACH_LOG_TAG, "camera: output target unavailable")
                        } else {
                            pendingCapture = target
                            takePicture.launch(target.uri)
                        }
                    }
                },
            )
            // File / Folder 全版本放出：30+ 走 all-files，<30 走运行时权限（机制与版本分叉见 PermissionSpec）
            OptionRow(
                title = stringResource(R.string.ui_home_attach_file),
                leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                onClick = { dismissThen { filePicker.launch(arrayOf("*/*")) } },
            )
            OptionRow(
                title = stringResource(R.string.ui_home_attach_folder),
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = { dismissThen { folderPicker.launch(null) } },
            )
        }
    }
}

/**
 * 相机输出目标：FileProvider 的 uri（给相机写）+ 它背后的临时文件（ingest 完成后删）。
 * 文件落在 `cacheDir/capture/`（见 `res/xml/file_paths.xml`）。
 */
private data class CameraTarget(val uri: Uri, val file: File)

/** cache 下専给相机输出的子目录（与 `file_paths.xml` 的 `path` 一致）。 */
private const val CAMERA_CAPTURE_DIR = "capture"

/**
 * 文件入口分流：这个 uri 能不能走图片管线。
 *
 * 判据优先看后缀（docId 通常就带着文件名），没有后缀才回落到 provider 报的 MIME——
 * 「下载」抽屉里在 DownloadManager 数据库中的文件就是裸数字 docId，只能靠 MIME。
 */
private fun isImportableImage(context: Context, uri: Uri, declaredType: String?): Boolean {
    val extension = uri.lastPathSegment.orEmpty()
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
    val isImage = if (extension.isNotEmpty()) {
        extension in IMPORTABLE_IMAGE_EXTENSIONS
    } else {
        declaredType?.startsWith("image/") == true
    }
    if (!isImage) return false
    // 超过图片管线上限的图会被 ingest 拒，而拒了之后草稿项是**静默消失**的，
    // 比「当一个文件附件」更糟。体积未知（-1）时放行，交给一致的上限去判。
    val size = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()
    return size == null || size <= IMAGE_MAX_BYTES
}

/**
 * 分流时认作图片的后缀。
 *
 * 不含 HEIC / HEIF：26/27 的 `BitmapFactory` 解不了，进来会在图片管线里静默失败，
 * 所以让它们继续走文件路径（= 今天的形态）。
 */
private val IMPORTABLE_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

/** 与图片管线的 `ImageFormat.MAX_IMAGE_BYTES` 同口径（那个常数在 agent-runtime 内部）。 */
private const val IMAGE_MAX_BYTES = 12 * 1024 * 1024L

/**
 * 造一个相机可以写的输出目标。路径与 provider 都对不上时返回 null（理论上不会）。
 */
private fun createCameraTarget(context: Context): CameraTarget? = runCatching {
    val dir = File(context.cacheDir, CAMERA_CAPTURE_DIR).apply { mkdirs() }
    val file = File(dir, "zafiro_capture_${System.currentTimeMillis()}.jpg")
    CameraTarget(
        uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        file = file,
    )
}.onFailure {
    Logger.w(ATTACH_LOG_TAG, "camera target create failed error=${it.message}")
}.getOrNull()

/**
 * 带写授权的 [ActivityResultContract]。
 *
 * 官方 `TakePicture` 只塞 `EXTRA_OUTPUT`，不带 clipData、不带 grant 标志；FileProvider 的
 * uri 必须显式授权给相机（另一个 uid），否则相机打开它会被拒。所以这里照官方「Take photos」
 * 文档补上 clipData + `FLAG_GRANT_READ/WRITE_URI_PERMISSION`。
 *
 * 回传原始 [resultCode] 而不是 Boolean：排障时必须区分 CANCELED 与其它码，
 * 官方 contract 把它丢了。
 */
private class GrantingTakePicture : ActivityResultContract<Uri, ActivityResult>() {

    override fun createIntent(context: Context, input: Uri): Intent {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // setClipData 返回 void，不能链式；grant 标志要配 clipData 才会落到被授的 uri 上
        intent.clipData = ClipData.newUri(context.contentResolver, "camera_output", input)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult =
        ActivityResult(resultCode, intent)
}

/**
 * 渲染层连续 User 气泡分组：两个相邻 UserBubble 之间有无内容，取决于上一 turn 的 blocks
 * 是否为空（内容夹在上一气泡与下一气泡之间）。规则：
 * - 对下一条黏（自身成为组首/组中）：自身 blocks 为空——自身内容会显示在自身气泡与下一条之间；
 * - 对上一条黏（自身成为组末/组中）：上一 turn blocks 为空。
 * 因此流式内容到达只会改变"自身→下一条"一侧，组首一侧的黏性由上一 turn 决定，组不随内容跳变。
 */
private fun userBubblePosition(turns: List<HomeChatTurn>, index: Int): UserBubblePosition {
    val current = turns.getOrNull(index)
    if (current == null || current.isCompactedDivider) return UserBubblePosition.Single
    val stickyUp = index > 0 && turns[index - 1].blocks.isEmpty() && !turns[index - 1].isCompactedDivider
    val stickyDown = index < turns.lastIndex && turns[index].blocks.isEmpty() && !turns[index + 1].isCompactedDivider
    return when {
        stickyUp && stickyDown -> UserBubblePosition.GroupMid
        stickyUp -> UserBubblePosition.GroupLast
        stickyDown -> UserBubblePosition.GroupFirst
        else -> UserBubblePosition.Single
    }
}

/**
 * 渲染层连续 User 气泡组（见 [userBubblePosition]）：index 所在组的组尾下标。
 * 组向下延伸的判定与 userBubblePosition 一致：自身 blocks 为空则下一气泡与本气泡相邻。
 */
internal fun userGroupTailIndex(turns: List<HomeChatTurn>, index: Int): Int {
    var tail = index
    while (tail < turns.lastIndex && turns[tail].blocks.isEmpty() && !turns[tail].isCompactedDivider && !turns[tail + 1].isCompactedDivider) tail++
    return tail
}

/** index 所在 User 组的组内全部用户文本：按序、双换行分隔、跳过空白（纯图片 turn）。 */
internal fun userGroupText(turns: List<HomeChatTurn>, index: Int): String {
    var head = index
    while (head > 0 && turns[head - 1].blocks.isEmpty() && !turns[head - 1].isCompactedDivider) head--
    val tail = userGroupTailIndex(turns, index)
    return turns.subList(head, tail + 1)
        .filterNot { it.isCompactedDivider }
        .map { it.userText }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

@Composable
private fun HomeChatTurnItem(
    turn: HomeChatTurn,
    userBubblePosition: UserBubblePosition,
    isLastTurn: Boolean,
    actionsDisplay: MessageActionsDisplay,
    /** 点组内任意一条时操作行归一到组尾 turn；非组尾成员仅作点击归一目标。 */
    userGroupTailTurnId: Long,
    /** 组尾 turn 的操作行资格（blocks 非空或为最后一条）。 */
    userGroupTappable: Boolean,
    /** 组内全部用户文本（双换行分隔），供组尾操作行复制。 */
    userGroupText: String,
    onContentTap: () -> Unit,
    onReGenerate: (Long) -> Unit,
    onFork: (Long) -> Unit,
    onRewind: (Long) -> Unit,
    expandedToolRuns: Set<String>,
    expandedToolResults: Set<String>,
    expandedThinking: Set<String>,
    onToggleToolRun: (Long, Int) -> Unit,
    onToggleToolResult: (Long, Int, Int) -> Unit,
    onToggleThinking: (Long, Int) -> Unit,
    expandedActionTurnId: Long?,
    expandedActionSource: ActionSource?,
    activeThinkingKey: String? = null,
    onToggleActionRow: (Long, ActionSource) -> Unit,
    isGenerating: Boolean,
    elapsedMs: Long? = null,
    elapsedStartedAtMs: Long? = null,
    branchIndex: Int = 1,
    branchCount: Int = 1,
    onSwitchBranch: (Int) -> Unit = {},
    turnOrdinal: Int = 0,
    usageText: String? = null,
    modifier: Modifier = Modifier,
) {
    if (turn.isCompactedDivider) {
        CompactedDividerItem(
            isCompacting = turn.isCompacting,
            modifier = modifier,
        )
        return
    }

    val alwaysVisible = actionsDisplay == MessageActionsDisplay.Always
    // Agent 操作行：仅正生成中的末轮隐藏；已结束的旧 turn 常显（复制无害，
    // 重新生成/分叉/回退在生成中点按弹 toast，见 guarded 回调）
    val canToggleAction = turn.blocks.isNotEmpty() && (!isGenerating || !isLastTurn)
    // User 操作行资格：生成中同样放开（历史遗留修复：复制无害，重新生成/回退由 Controller 护栏拦截）；
    // 最后一条 turn 即使无内容也放开，使本条 query 仍可复制
    val userRowEligible = turn.blocks.isNotEmpty() || isLastTurn
    val isUserGroupTail = turn.id == userGroupTailTurnId

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun copyText(text: String) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
        }
        Toast.makeText(context, R.string.ui_toast_copied, Toast.LENGTH_SHORT).show()
    }

    fun toastGenerating() {
        Toast.makeText(context, R.string.ui_toast_generating, Toast.LENGTH_SHORT).show()
    }
    // 生成中：危险动作（重新生成/分叉/回退）弹 toast 提示，不透传给 Controller（其护栏会静默吞掉）
    val guardedReGenerate: (Long) -> Unit = { id ->
        if (isGenerating) toastGenerating() else onReGenerate(id)
    }
    val guardedFork: (Long) -> Unit = { id ->
        if (isGenerating) toastGenerating() else onFork(id)
    }
    val guardedRewind: (Long) -> Unit = { id ->
        if (isGenerating) toastGenerating() else onRewind(id)
    }

    val isActionExpanded = expandedActionTurnId == turn.id
    val actionSource = expandedActionSource
    var showActionRow by remember { mutableStateOf(false) }
    LaunchedEffect(isActionExpanded) {
        showActionRow = isActionExpanded
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BlockSpacing),
    ) {
        // 附件行：图片与文件同一行（同尺寸同形状），镜像 UserMessageBubble 的
        // 对齐方式——BoxWithConstraints 右对齐，Row 贴内容宽、max 同 bubble（0.82f）；
        // 外层与卡片同圆角 clip——边缘卡被裁时仍呈圆角。多附件时初始 scroll=0 优先展示左边的，整行靠右。
        // 不可点击（TODO: 点开大图 / 打开文件）
        if (turn.images.isNotEmpty() || turn.files.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                HomeChatAttachmentRow(
                    images = turn.images,
                    files = turn.files,
                    cardSize = 120.dp,
                    cornerRadius = UserBubbleCornerRadius,
                    modifier = Modifier.widthIn(max = maxWidth * 0.82f),
                )
            }
        }

        // 纯图片 turn：文本为空（isEmpty 而非 isBlank——历史里用户发过的空白文本
        // turn 仍显示气泡）；连 clickable 一起隐藏，空 bubble 点击弹操作行无意义
        if (turn.userText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onContentTap()
                            // Always 模式永不收回；OnTap 模式点组内任意一条都归一到组尾
                            if (!alwaysVisible && userGroupTappable) {
                                onToggleActionRow(userGroupTailTurnId, ActionSource.User)
                            }
                        },
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                UserMessageBubble(text = turn.userText, position = userBubblePosition)
            }
        }

        AnimatedVisibility(
            visible = isUserGroupTail && userRowEligible &&
                    (alwaysVisible || (showActionRow && actionSource == ActionSource.User)),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TurnActionRow(
                source = ActionSource.User,
                display = actionsDisplay,
                onCopy = {
                    copyText(userGroupText)
                },
                onReGenerate = { guardedReGenerate(turn.id) },
                onFork = { guardedFork(turn.id) },
                onRewind = { guardedRewind(turn.id) },
            )
        }

        // 内容区：turn 分隔补差（TurnSeparator - BlockSpacing）放在内容区顶部而非气泡底部，
        // 使气泡→用户操作行（复制按钮）的间距仅剩 BlockSpacing 12dp，与 markdown→操作行一致；
        // 组内成员（非 Single）不设补差，与下一气泡保持组内紧凑
        if (turn.blocks.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(
                    top = if (userBubblePosition == UserBubblePosition.Single) {
                        TurnSeparator - BlockSpacing
                    } else {
                        0.dp
                    }
                ),
                verticalArrangement = Arrangement.spacedBy(BlockSpacing),
            ) {
                var blockIndex = 0
                while (blockIndex < turn.blocks.size) {
                    // Collect consecutive Tool blocks into a run
                    val runStart = blockIndex
                    var runEnd = runStart
                    while (runEnd < turn.blocks.size && turn.blocks[runEnd] is HomeChatBlock.Tool) {
                        runEnd++
                    }
                    val runSize = runEnd - runStart
                    if (runSize >= 1) {
                        val statuses = turn.blocks.subList(runStart, runEnd)
                            .map { (it as HomeChatBlock.Tool).status }
                        val runKey = "${turn.id}_${runStart}"
                        val runResults = expandedToolResults
                            .filter { it.startsWith("${runKey}_") }
                            .mapNotNull { it.removePrefix("${runKey}_").toIntOrNull() }
                            .toSet()
                        ToolChain(
                            tools = statuses,
                            isExpanded = runKey in expandedToolRuns,
                            expandedResults = runResults,
                            onToggleRun = { onToggleToolRun(turn.id, runStart) },
                            onToggleResult = { ti ->
                                onToggleToolResult(turn.id, runStart, ti)
                            },
                            onContentClick = {
                                onContentTap()
                                if (!alwaysVisible && canToggleAction) {
                                    onToggleActionRow(turn.id, ActionSource.Agent)
                                }
                            },
                        )
                        blockIndex = runEnd
                    } else {
                        when (val block = turn.blocks[blockIndex]) {
                            is HomeChatBlock.Text -> {
                                if (block.text.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    onContentTap()
                                                    if (!alwaysVisible && canToggleAction) {
                                                        onToggleActionRow(
                                                            turn.id,
                                                            ActionSource.Agent
                                                        )
                                                    }
                                                },
                                            ),
                                    ) {
                                        AssistantOutputText(
                                            text = block.text,
                                        )
                                    }
                                }
                            }

                            is HomeChatBlock.Error -> {
                                val errorUi = toAssistantErrorUi(
                                    message = block.message,
                                    code = block.code,
                                    attempts = block.attempts,
                                )
                                CollapsibleBlock(
                                    icon = Icons.Filled.ErrorOutline,
                                    title = stringResource(errorUi.titleRes),
                                    isExpanded = true,
                                    onToggle = {},
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    nonCollapsible = true,
                                ) {
                                    val body = errorUi.bodyRes?.let { stringResource(it) }
                                        ?: errorUi.body.orEmpty()
                                    if (body.isNotEmpty()) {
                                        ToolResultText(
                                            text = body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            is HomeChatBlock.Retrying -> {
                                // 常展开、不可折叠（与 Error 块不同：retry 是进行中的转态，
                                // 收起无意义）；重试成功即整个块消失
                                CollapsibleBlock(
                                    icon = Icons.Filled.Refresh,
                                    title = stringResource(R.string.ui_home_retrying_title),
                                    isExpanded = true,
                                    onToggle = {},
                                    isRunning = true,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    nonCollapsible = true,
                                ) {
                                    val count = stringResource(
                                        R.string.ui_home_retrying_attempt,
                                        block.attempt,
                                        block.maxAttempts,
                                    )
                                    ToolResultText(
                                        text = if (block.reason.isBlank()) count
                                        else "$count: ${block.reason.trim()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            is HomeChatBlock.Thinking -> {
                                // blockIndex 是 var，lambda 捕获按引用；先快照成 val 再进 lambda
                                val blockIndexNow = blockIndex
                                val thinkingKey = "${turn.id}_$blockIndexNow"
                                val isThinkingExpanded = thinkingKey in expandedThinking
                                val thinkingTitle =
                                    stringResource(R.string.ui_home_thinking_title)
                                CollapsibleBlock(
                                    icon = ToolPresentation.Thinking,
                                    title = thinkingTitle + ToolPresentation
                                        .previewOf(block.text)
                                        ?.let { " · $it" }
                                        .orEmpty(),
                                    isExpanded = isThinkingExpanded,
                                    onToggle = { onToggleThinking(turn.id, blockIndexNow) },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    onContentTap()
                                                    if (!alwaysVisible && canToggleAction) {
                                                        onToggleActionRow(
                                                            turn.id,
                                                            ActionSource.Agent
                                                        )
                                                    }
                                                },
                                            ),
                                    ) {
                                        ToolResultText(
                                            text = block.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            // active 思考块展开时滚到底跟随；用户可手动滚动不锁
                                            autoScrollToEnd = isThinkingExpanded && thinkingKey == activeThinkingKey,
                                        )
                                    }
                                }
                            }

                            is HomeChatBlock.Tool -> {} // handled above
                        }
                        blockIndex++
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = (alwaysVisible && canToggleAction) ||
                    (showActionRow && actionSource == ActionSource.Agent),
            enter = expandVertically() + fadeIn(),
        ) {
            TurnActionRow(
                source = ActionSource.Agent,
                display = actionsDisplay,
                onCopy = {
                    val text = turn.blocks
                        .filterIsInstance<HomeChatBlock.Text>()
                        .joinToString("\n\n") { it.text }
                    copyText(text)
                },
                onReGenerate = { guardedReGenerate(turn.id) },
                onFork = { guardedFork(turn.id) },
                // 翻页器由该轮自身分支数驱动，各轮次独立显示
                branchIndex = branchIndex,
                branchCount = branchCount,
                onSwitchBranch = onSwitchBranch,
                elapsedMs = elapsedMs,
                startedAtMs = elapsedStartedAtMs,
                usageText = usageText,
                statsSteps = turn.steps,
                statsTurnIndex = turnOrdinal,
                statsDurationMs = turn.durationMs,
                // 单轮口径：本行只显示本轮输出；上下文单独作为命中率分母
                statsTokens = turn.outputTokens,
                statsPrompt = turn.inputTokens,
                statsOutputTokens = turn.outputTokens,
                statsCacheRead = turn.cacheReadTokens,
                statsAtMs = turn.atMs,
            )
        }
    }
}

@Composable
private fun CompactedDividerItem(
    isCompacting: Boolean,
    modifier: Modifier = Modifier,
) {
    val dots = if (isCompacting) {
        val dotCount by produceState(initialValue = 1) {
            while (true) {
                delay(400)
                value = (value % 3) + 1
            }
        }
        ".".repeat(dotCount)
    } else {
        ""
    }

    Row(
        modifier = modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
        Text(
            text = if (isCompacting) {
                "${stringResource(R.string.ui_home_conversation_compacting)}$dots"
            } else {
                stringResource(R.string.ui_home_conversation_compacted)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
    }
}

/**
 * 手势信任的贴底跟随状态机（外层聊天列表与内层 thinking/工具结果文本共用）。
 * 用户开始拖拽立即暂停跟随——流式新内容不再抢占滚动；
 * 滚动完全停止后按 [isAtEnd] 判定是否恢复跟随。
 * 返回 MutableState：发送消息等场景可主动置回 true 恢复跟随。
 */
@Composable
internal fun rememberScrollFollowState(
    interactionSource: InteractionSource,
    isScrollInProgress: () -> Boolean,
    isAtEnd: () -> Boolean,
): MutableState<Boolean> {
    val follow = remember { mutableStateOf(true) }
    var hasPendingUserScrollDecision by remember { mutableStateOf(false) }
    val isUserDragging by interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            follow.value = false
            hasPendingUserScrollDecision = true
        }
    }
    LaunchedEffect(interactionSource) {
        snapshotFlow { isScrollInProgress() }
            .collectLatest { isScrollInProgress ->
                if (!isScrollInProgress && hasPendingUserScrollDecision) {
                    follow.value = isAtEnd()
                    hasPendingUserScrollDecision = false
                }
            }
    }
    return follow
}

@Composable
private fun HomeTitleConfigChip(
    uiStateProvider: () -> HomeChatUiState,
    onChipClick: () -> Unit,
    onAnchorBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = uiStateProvider()
    val isDarkTheme = LocalAppDarkTheme.current
    val activeConfig = uiState.displayConfig
    val titleText = activeConfig?.name ?: stringResource(R.string.app_name)
    val hasConfigs = uiState.savedConfigs.isNotEmpty()

    val latestOnAnchorBoundsChanged by rememberUpdatedState(onAnchorBoundsChanged)
    val latestOnChipClick by rememberUpdatedState(onChipClick)
    var chipCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Row(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                chipCoordinates = coordinates
                if (coordinates.isAttached) {
                    latestOnAnchorBoundsChanged(coordinates.boundsInRoot())
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = hasConfigs,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    chipCoordinates?.takeIf { it.isAttached }?.boundsInRoot()?.let { bounds ->
                        latestOnAnchorBoundsChanged(bounds)
                    }
                    latestOnChipClick()
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = titleText,
            style = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkTheme) Color.White else Color.Black,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasConfigs) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
            )
        }
    }
}


@Composable
private fun HistoryPanelInPlace(
    onConversationSelected: (String) -> Unit,
    onNewConversation: () -> Unit,
) {
    val viewModel = com.hoerire.tools.uikit.infra.nav.pageViewModel<ConversationHistoryViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.sendIntent(ConversationHistoryIntent.Load)
    }
    ConversationHistoryPageContent(
        uiState = uiState,
        onConversationClick = { id ->
            viewModel.sendIntent(ConversationHistoryIntent.ClearUnread(id))
            onConversationSelected(id)
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
        onNewConversation = onNewConversation,
    )
}