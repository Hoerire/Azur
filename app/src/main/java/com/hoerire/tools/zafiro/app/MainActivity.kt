package com.hoerire.tools.zafiro.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.hoerire.tools.logging.Logger
import com.hoerire.tools.uikit.infra.GlobalTouchDispatcher
import com.hoerire.tools.zafiro.app.crash.CrashRecorder
import com.hoerire.tools.zafiro.app.ui.UpdateDialogHost
import com.hoerire.tools.zafiro.app.ui.ZafiroApp
import com.hoerire.tools.zafiro.app.ui.model.AppLaunchDecision
import com.hoerire.tools.zafiro.app.ui.model.ThemeController
import com.hoerire.tools.zafiro.business.application.ApplicationService
import com.hoerire.tools.zafiro.repo.UpdateCheckHolder
import kotlinx.coroutines.launch
import com.hoerire.tools.zafiro.repo.XRepo
import com.hoerire.tools.zafiro.service.findService
import com.hoerire.tools.zafiro.service.requireService
import kotlinx.coroutines.runBlocking

// tag:zafiro | tag:nexus-x-log | message:zafiro | message:nexus-x-log
class MainActivity : AppCompatActivity() {
    private fun applyLanguageTag(tag: String) {
        // 始终显式设置：空 tag = 清除应用内语言，回落系统；否则用户指定优先
        AppCompatDelegate.setApplicationLocales(
            if (tag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            },
        )
    }

    // launcher 必须在 STARTED 前注册：MainActivity 预注册 → ApplicationService 持有结果路由
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        requireService<ApplicationService>().onRuntimePermissionResult(granted)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // launcher 必须在 STARTED 前注册：预注册后装进 ApplicationService
        requireService<ApplicationService>()
            .installPermissionLauncher(permissionLauncher)
        val launchDecision = runBlocking {
            val decision = AppLaunchDecision.resolve()
            // 同步读主题偏好：深色模式冷启动首帧不能闪白
            ThemeController.load()
            // 回填型设置 flow 的冷启动回填（首帧真值，防“进设置页才生效”类 bug）
            runCatching { XRepo.hydrateSettings() }
                .onFailure { Logger.w("zafiro_Main", "hydrate failed ${it.message}") }
            decision
        }
        applyLanguageTag(launchDecision.languageTag)
        // 秒崩（启动窗口内崩）的报告不能依赖 UI 去取：首帧前崩掉时弹窗永远没机会出现。
        // 放在 setContent 之前、语言生效之后：最早期拿到报告，文案语言也对。
        CrashRecorder.consumeStartupCrash(this)

        handleIncomingIntent(intent)

        // SU 门槛 + 首次启动自动部署环境
        when (Bootstrap.gate(this)) {
            Bootstrap.Gate.EXIT -> return
            Bootstrap.Gate.SETUP -> Unit
            Bootstrap.Gate.READY -> Unit
        }

        // 冷启动检查一次更新，结果由 UpdateDialogHost 负责展示
        lifecycleScope.launch {
            UpdateCheckHolder.runOnce(BuildConfig.VERSION_NAME)
        }

        setContent {
            ZafiroApp(
                launchDecision = launchDecision,
            )
            UpdateDialogHost()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val targetConversationId =
            intent?.getStringExtra(ApplicationService.EXTRA_TARGET_CONVERSATION_ID)
        findService<ApplicationService>()?.handleNavigationIntent(targetConversationId)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        GlobalTouchDispatcher.dispatch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        // 前台跟踪由 ApplicationService 经 lifecycle callbacks 自动维护，无需手动转发
    }
}