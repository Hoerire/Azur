package com.hoerire.tools.zafiro.app.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.hoerire.tools.uikit.base.BaseTheme
import com.hoerire.tools.uikit.infra.LiquidMenuItem
import com.hoerire.tools.uikit.infra.LiquidPopover
import com.hoerire.tools.uikit.infra.LiquidScreen
import com.hoerire.tools.uikit.infra.LiquidScreenSwipeContent
import com.hoerire.tools.uikit.infra.LiquidScreenTitle
import com.hoerire.tools.uikit.infra.PopoverAlignment
import com.hoerire.tools.uikit.infra.TitleDirection
import com.hoerire.tools.uikit.infra.nav.LocalNavigationEntry
import com.hoerire.tools.uikit.infra.nav.LocalPageTitle
import com.hoerire.tools.uikit.infra.nav.rememberNavigationController
import com.hoerire.tools.uikit.infra.rememberLiquidScreenState
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.crash.CrashRecorder
import com.hoerire.tools.zafiro.app.crash.CrashReport
import com.hoerire.tools.zafiro.app.crash.CrashReportDialog
import com.hoerire.tools.zafiro.app.ui.content.LocalMarkdownScale
import com.hoerire.tools.zafiro.app.ui.model.AppLaunchDecision
import com.hoerire.tools.zafiro.app.ui.model.ThemeController
import com.hoerire.tools.zafiro.app.ui.model.home.HomeChatViewModel
import com.hoerire.tools.zafiro.app.ui.nav.HomePage
import com.hoerire.tools.zafiro.app.ui.nav.NoTitle
import com.hoerire.tools.zafiro.app.ui.nav.PageTitleSpec
import com.hoerire.tools.zafiro.app.ui.nav.ResTitle
import com.hoerire.tools.zafiro.app.ui.nav.TextTitle
import com.hoerire.tools.zafiro.app.ui.nav.TitleBarMode
import com.hoerire.tools.zafiro.app.ui.nav.TopBarActionSpec
import com.hoerire.tools.zafiro.app.ui.nav.ZafiroPage
import com.hoerire.tools.zafiro.business.application.ApplicationService
import com.hoerire.tools.zafiro.repo.XRepo
import com.hoerire.tools.zafiro.service.findService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hoerire.tools.zafiro.app.ui.nav.OnboardingThemePage
import com.hoerire.tools.zafiro.app.ui.nav.OnboardingPreferencesPage
import com.hoerire.tools.zafiro.app.ui.nav.DonePage
import com.hoerire.tools.zafiro.app.ui.nav.ProviderPickPage
import com.hoerire.tools.zafiro.app.ui.nav.ConversationHistoryPage
import com.hoerire.tools.zafiro.app.ui.nav.UsagePanelPage
import com.hoerire.tools.zafiro.app.ui.nav.SettingsHomePage
import com.hoerire.tools.zafiro.app.ui.PageChromeMenuItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip

@Composable
fun ZafiroApp(
    launchDecision: AppLaunchDecision,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activity = context as? Activity
    val systemDarkTheme = isSystemInDarkTheme()
    val themePrefs = ThemeController.prefs
    val markdownScale by XRepo.markdownScaleSetting.collectAsState()
    val isDarkTheme = themePrefs.resolveDarkTheme(systemDarkTheme)
    val actionIconTint = if (isDarkTheme) Color.White else Color.Black
    val rootBackToHomeWindowMillis = 2_000L
    val rootBackToHomeHint = stringResource(R.string.ui_root_back_to_home_hint)
    val pageChromeHost = rememberPageChromeHost()
    var chromeMenuExpanded by remember { mutableStateOf(false) }
    var rightButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var lastRootBackPressedAt by remember { mutableStateOf(0L) }
    var isPageTransitioning by remember { mutableStateOf(false) }
    var pendingCrashReport by remember { mutableStateOf<CrashReport?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CrashRecorder.peekPendingCrash(context)
        }?.let { report ->
            pendingCrashReport = report
        }
    }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        findService<ApplicationService>()?.conversationSwitchRequests?.collect { convId ->
            selectedConversationId = convId
        }
    }
    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var newConversationRequested by remember { mutableStateOf(false) }
    val initialPage = launchDecision.initialPage
    val controller = rememberNavigationController<ZafiroPage>(initialPage = initialPage)
    val navigator = controller.navigator
    val saveableStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(controller.stack) {
        val activeIds = controller.stack.map { it.id }.toSet()
        pageChromeHost.syncWithStack(activeIds)
    }

    val currentEntry = controller.currentEntry
    val currentPage = currentEntry.page
    val currentChrome = pageChromeHost.stateFor(currentEntry.id)
    // ── App 级顶栏 pill：所有页面常驻（开机引导除外），页面不再各自注册 ──
    val isOnboardingPage = currentPage is OnboardingThemePage ||
        currentPage is OnboardingPreferencesPage ||
        currentPage is DonePage ||
        currentPage is ProviderPickPage
    val historyMenuLabel = stringResource(R.string.dsh_menu_history)
    val usageMenuLabel = stringResource(R.string.dsh_panel_usage)
    val settingsMenuLabel = stringResource(R.string.ui_settings_menu_entry)
    val appBarItems: List<PageChromeMenuItem> = if (isOnboardingPage) {
        emptyList()
    } else {
        listOf(
            PageChromeMenuItem("history", historyMenuLabel) {
                if (currentPage !is ConversationHistoryPage) navigator.push(ConversationHistoryPage)
            },
            PageChromeMenuItem("usage", usageMenuLabel) {
                if (currentPage !is UsagePanelPage) navigator.push(UsagePanelPage)
            },
            PageChromeMenuItem("settings", settingsMenuLabel) {
                if (currentPage !is SettingsHomePage) navigator.push(SettingsHomePage)
            },
        )
    }
    val chromeBarExtension: (@Composable () -> Unit)? = remember(appBarItems) {
        if (appBarItems.isEmpty()) null else { { PageChromeBarRow(appBarItems) } }
    }
    val isChromeMenuOpen = chromeMenuExpanded && currentChrome.menuItems.isNotEmpty()
    val rightButtonAlpha by animateFloatAsState(
        targetValue = if (isChromeMenuOpen) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "rightButtonAlpha",
    )
    val currentLeftAction = currentChrome.leftAction ?: currentPage.leftAction
    val latestCurrentChrome by rememberUpdatedState(currentChrome)
    fun closeChromeMenu() {
        chromeMenuExpanded = false
    }


    // 返回浮钮彻底移除：返回统一走系统返回手势 / 顶栏 pill
    val showLeftButton = false
    // 只保留页面自己的动作钮（如配置页「添加」）；
    // 历史页的右钮是返回箭头、其余页的 MoreHoriz 是菜单浮钮 —— 都不要
    val hasChromeMenuItems = currentChrome.menuItems.isNotEmpty()
    val resolvedRightAction = currentPage.rightAction ?: currentChrome.rightAction
    val showRightButton = currentChrome.actionsVisible && (resolvedRightAction != null || hasChromeMenuItems)
    val currentTitle = resolveScreenTitle(currentChrome, currentPage, currentEntry.id)
    val isTitleCollapsible = currentPage.titleMode == TitleBarMode.Collapsible
    val screenState = rememberLiquidScreenState(
        title = currentTitle,
        isTitleCollapsible = isTitleCollapsible,
        showLeftButton = showLeftButton,
        showRightButton = showRightButton,
        showBlurLayer = currentPage.showBlurLayer,
    )

    fun push(page: ZafiroPage) {
        closeChromeMenu()
        navigator.push(page)
    }

    fun pushFromLeft(page: ZafiroPage) {
        closeChromeMenu()
        navigator.push(page, direction = TitleDirection.Back)
    }

    fun resetTo(page: ZafiroPage) {
        closeChromeMenu()
        navigator.resetTo(page)
    }

    fun popToRight() {
        navigator.pop(direction = TitleDirection.Forward)
    }

    fun popOrMoveTaskToBack() {
        if (controller.canGoBack && currentPage.backEnabled) {
            navigator.pop()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastRootBackPressedAt <= rootBackToHomeWindowMillis) {
            activity?.moveTaskToBack(true)
        } else {
            lastRootBackPressedAt = now
            Toast.makeText(context.applicationContext, rootBackToHomeHint, Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun requestBack() {
        if (chromeMenuExpanded) {
            closeChromeMenu()
            return
        }
        val backHandler = latestCurrentChrome.backHandler
        if (backHandler != null && backHandler.shouldConsumeBack()) {
            backHandler.onConsumeBack()
        } else {
            popOrMoveTaskToBack()
        }
    }

    suspend fun deleteActiveConversation(id: String) {
        val homeEntry = controller.stack.lastOrNull { entry -> entry.page == HomePage }
            ?: error("Home entry is required before deleting the active conversation.")
        val homeViewModel = ViewModelProvider(homeEntry)[
            HomeChatViewModel::class.java.name,
            HomeChatViewModel::class.java,
        ]
        homeViewModel.deleteConversationNow(id)
        if (activeConversationId == id) {
            activeConversationId = null
        }
    }

    BackHandler(enabled = true) {
        requestBack()
    }

    val onLeftClick = bindAction(currentLeftAction, fallback = ::requestBack)
    val onRightClick = bindAction(resolvedRightAction)

    // 上次已做过标题动画的条目：标题滑动只表达「换了页面」这一空间语义。
    // 同一条目内的标题刷新（会话标题出现、选中数量变化、切换配置）是活内容变了，
    // 不是位移，走 update 瞬切；否则它们会沿用上一次的导航方向反复左右滑。
    var lastAnimatedEntryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        currentEntry.id,
        controller.lastDirection,
        currentTitle,
        currentLeftAction,
        resolvedRightAction,
        currentChrome.menuItems,
        currentChrome.backHandler,
        currentChrome.actionsVisible,
    ) {
        if (currentChrome.menuItems.isEmpty()) {
            closeChromeMenu()
        }

        val isPageChange = lastAnimatedEntryId != currentEntry.id
        lastAnimatedEntryId = currentEntry.id
        val titleDirection =
            if (isPageChange) controller.lastDirection else TitleDirection.None

        when (titleDirection) {
            TitleDirection.Forward -> screenState.navigateForward(
                title = currentTitle,
                isTitleCollapsible = isTitleCollapsible,
                showLeftButton = showLeftButton,
                showRightButton = showRightButton,
                showBlurLayer = currentPage.showBlurLayer,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
            )

            TitleDirection.Back -> screenState.navigateBack(
                title = currentTitle,
                isTitleCollapsible = isTitleCollapsible,
                showLeftButton = showLeftButton,
                showRightButton = showRightButton,
                showBlurLayer = currentPage.showBlurLayer,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
            )

            TitleDirection.None -> screenState.update(
                title = currentTitle,
                isTitleCollapsible = isTitleCollapsible,
                showLeftButton = showLeftButton,
                showRightButton = showRightButton,
                showBlurLayer = currentPage.showBlurLayer,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
            )
        }
    }

    val seedColor = themePrefs.seedColor?.let { Color(it) }

    Box(modifier = Modifier.fillMaxSize()) {
        BaseTheme(
            darkTheme = isDarkTheme,
            dynamicColor = themePrefs.seedColor == null,
            seedColor = seedColor,
        ) {
            CompositionLocalProvider(
                LocalMarkdownScale provides markdownScale,
            ) {
                LiquidScreen(
                    state = screenState,
                    // 折叠信号载体归属当前导航条目，由 LiquidScreen 经 nestedScroll 自动写入；
                    // 条目存活期保留，导航切页同帧隔离，返回时首帧恢复离开前状态。
                    titleBarScroll = currentEntry.titleBarScroll,
                    actionsEnabled = !isPageTransitioning,
                    barItems = chromeBarExtension,
                    barItemsHeight = if (chromeBarExtension == null) 0.dp else 26.dp,
                    isOccluded = isChromeMenuOpen || currentChrome.isTitleOccluded,
                    rightButtonAlpha = rightButtonAlpha,
                    showLeftButton = showLeftButton,
                    showRightButton = showRightButton,
                    onLeftClick = onLeftClick,
                    onRightClick = onRightClick,
                    onRightButtonBoundsChanged = { rightButtonBounds = it },
                    leftButton = currentLeftAction?.let { action ->
                        {
                            AnimatedContent(
                                targetState = action.icon,
                                transitionSpec = {
                                    val iconAnimationSpec = tween<Float>(
                                        durationMillis = 280,
                                        easing = FastOutSlowInEasing,
                                    )
                                    (scaleIn(
                                        initialScale = 1.18f,
                                        animationSpec = iconAnimationSpec,
                                    ) + fadeIn(animationSpec = iconAnimationSpec)).togetherWith(
                                        scaleOut(
                                            targetScale = 0.78f,
                                            animationSpec = iconAnimationSpec,
                                        ) + fadeOut(animationSpec = iconAnimationSpec)
                                    )
                                },
                                label = "leftActionIcon",
                            ) { imageVector ->
                                ActionBarVectorIcon(
                                    imageVector = imageVector,
                                    contentDescription = action.contentDescription,
                                    tint = actionIconTint,
                                )
                            }
                        }
                    },
                    rightButton = if (showRightButton) {
                        {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 选择态批量菜单入口（历史页 menuItems）
                                if (hasChromeMenuItems) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .onGloballyPositioned { coords ->
                                                rightButtonBounds = coords.boundsInRoot()
                                            }
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { chromeMenuExpanded = true },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ActionBarVectorIcon(
                                            imageVector = Icons.Default.MoreHoriz,
                                            contentDescription = "More Button",
                                            tint = actionIconTint,
                                        )
                                    }
                                }
                                resolvedRightAction?.let { action ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { action.onClick?.invoke() ?: onRightClick?.invoke() },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ActionBarVectorIcon(
                                            imageVector = action.icon,
                                            contentDescription = action.contentDescription,
                                            tint = actionIconTint,
                                        )
                                    }
                                }
                            }
                        }
                    } else null,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LiquidScreenSwipeContent(
                            targetState = currentEntry,
                            direction = controller.lastDirection,
                            modifier = Modifier.fillMaxSize(),
                            onTransitionActiveChanged = { active ->
                                isPageTransitioning = active
                            },
                        ) { entry ->
                            val pageChromeRegistrar = remember(entry.id, pageChromeHost) {
                                pageChromeHost.registrarFor(entry.id)
                            }
                            CompositionLocalProvider(
                                LocalNavigationEntry provides entry,
                                LocalPageChrome provides pageChromeRegistrar,
                                LocalPageTitle provides resolveTitle(entry.page.titleSpec),
                            ) {
                                saveableStateHolder.SaveableStateProvider(entry.id) {
                                    ZafiroPageContent(
                                        entry = entry,
                                        newConversationRequested = newConversationRequested,
                                        onNewConversationRequested = { com.hoerire.tools.zafiro.app.ui.model.home.HomeChatStartupRequests.requestNewConversation() },
                                        onNewConversationRequestConsumed = { newConversationRequested = false },
                                        isTopPage = (currentEntry.id == entry.id),
                                        onPush = ::push,
                                        onPushFromLeft = ::pushFromLeft,
                                        onPop = { navigator.pop() },
                                        onPopMultiple = { navigator.popMultiple(it) },
                                        onPopToRight = ::popToRight,
                                        onResetTo = ::resetTo,
                                        selectedConversationId = selectedConversationId,
                                        onConversationSelected = { id ->
                                            selectedConversationId = id
                                        },
                                        onConversationSelectionConsumed = { id ->
                                            if (selectedConversationId == id) {
                                                selectedConversationId = null
                                            }
                                        },
                                        activeConversationId = activeConversationId,
                                        onActiveConversationChanged = { id ->
                                            activeConversationId = id
                                        },
                                        onCurrentConversationDeleted = { id ->
                                            deleteActiveConversation(id)
                                        },
                                    )
                                }
                            }
                        }

                        LiquidPopover(
                            expanded = isChromeMenuOpen,
                            onDismissRequest = ::closeChromeMenu,
                            anchorBounds = rightButtonBounds,
                            alignment = PopoverAlignment.BottomEnd,
                        ) {
                            currentChrome.menuItems.forEach { item ->
                                LiquidMenuItem(
                                    title = item.title,
                                    onClick = {
                                        closeChromeMenu()
                                        item.onClick()
                                    },
                                )
                            }
                        }

                        pendingCrashReport?.let { report ->
                            CrashReportDialog(
                                report = report,
                                onDismiss = { pendingCrashReport = null },
                            )
                        }
                    }
                }
                currentChrome.overlay?.invoke()
            }
        }
    }
}

@Composable
private fun resolveScreenTitle(
    chrome: PageChromeContribution,
    page: ZafiroPage,
    entryId: String,
): LiquidScreenTitle {
    val key = chrome.titleKey ?: entryId
    chrome.titleContent?.let { return LiquidScreenTitle.Content(content = it, key = key) }
    return LiquidScreenTitle.Text(
        value = resolveTitle(chrome.titleSpec ?: page.titleSpec),
        key = key
    )
}

@Composable
private fun resolveTitle(titleSpec: PageTitleSpec): String {
    return when (titleSpec) {
        NoTitle -> ""
        is ResTitle -> stringResource(titleSpec.resId)
        is TextTitle -> titleSpec.value
    }
}

private fun bindAction(
    action: TopBarActionSpec?,
    fallback: (() -> Unit)? = null,
): (() -> Unit)? {
    return action?.onClick ?: fallback
}


@Composable
private fun ActionBarVectorIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    size: Dp = 20.dp,
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
