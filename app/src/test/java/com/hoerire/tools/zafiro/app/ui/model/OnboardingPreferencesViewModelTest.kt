// 保护：Onboarding 一键开启及完成向导时，悬浮球/常驻通知开关随授权状态同步落盘；
// 且「执行规则」页是默认规则的减配视图——不动它就写回与默认逐字相同的列表。
package com.hoerire.tools.zafiro.app.ui.model

import android.content.Context
import android.content.ContextWrapper
import com.hoerire.tools.zafiro.util.AppContext
import com.hoerire.tools.zafiro.app.util.SilentLoggerRule
import com.hoerire.tools.zafiro.business.permission.Channel
import com.hoerire.tools.zafiro.business.permission.Permission
import com.hoerire.tools.zafiro.business.permission.PermissionManager
import com.hoerire.tools.zafiro.business.permission.PermissionResult
import com.hoerire.tools.zafiro.business.permission.PermissionScope
import com.hoerire.tools.zafiro.business.permission.PermissionState
import com.hoerire.tools.zafiro.repo.FakeDomainSettingsStore
import com.hoerire.tools.zafiro.repo.LocalSettingsDefaults
import com.hoerire.tools.zafiro.repo.XRepo
import com.hoerire.tools.zafiro.service.ServiceRegistry
import com.hoerire.tools.zafiro.service.installService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPreferencesViewModelTest {

    @get:Rule
    val silentLoggerRule = SilentLoggerRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tempDir = Files.createTempDirectory("test_onboarding_prefs").toFile()

    private val context: Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = tempDir
    }

    @Before
    fun setUp() {
        AppContext.provide(context)
        XRepo.installStoreForTest(FakeDomainSettingsStore())
        XRepo.init(context)
    }

    @After
    fun tearDown() {
        ServiceRegistry.clearForTest()
        XRepo.resetForTest()
    }

    @Test
    fun requestAll_enablesFloatingBallAndResidentNotificationWhenGranted() = runTest {
        installService<PermissionManager>(FakePermissionManager(PermissionState.GRANTED))
        val viewModel = OnboardingPreferencesViewModel()

        assertFalse(XRepo.floatingBallEnabled())
        assertFalse(XRepo.residentNotificationEnabled())

        viewModel.sendIntent(OnboardingPreferencesIntent.RequestAll)
        advanceUntilIdle()

        assertTrue(XRepo.floatingBallEnabled())
        assertTrue(XRepo.residentNotificationEnabled())
    }

    @Test
    fun complete_enablesFloatingBallAndResidentNotificationWhenPermissionsGranted() = runTest {
        installService<PermissionManager>(FakePermissionManager(PermissionState.GRANTED))
        val viewModel = OnboardingPreferencesViewModel()

        assertFalse(XRepo.floatingBallEnabled())
        assertFalse(XRepo.residentNotificationEnabled())

        viewModel.sendIntent(OnboardingPreferencesIntent.Complete)
        advanceUntilIdle()

        assertTrue(XRepo.floatingBallEnabled())
        assertTrue(XRepo.residentNotificationEnabled())
        assertTrue(XRepo.onboardingCompleted())
    }

    // 保护：onboarding 页是默认规则的减配视图。不动它就不能把默认写坏——
    // 漏了它，这里又会退回写死一份更粗的列表，与设置页的默认分叉（本次修的 bug）。
    @Test
    fun complete_withoutEditing_persistsExactlyTheDefaultPatterns() = runTest {
        installService<PermissionManager>(FakePermissionManager(PermissionState.GRANTED))
        val viewModel = OnboardingPreferencesViewModel()

        viewModel.sendIntent(OnboardingPreferencesIntent.Load)
        advanceUntilIdle()
        viewModel.sendIntent(OnboardingPreferencesIntent.Complete)
        advanceUntilIdle()

        val persisted = XRepo.executionRules.get(LocalSettingsDefaults.DEFAULT_EXECUTION_RULE_ID)
        assertEquals(LocalSettingsDefaults.defaultExecutionPatterns, persisted?.patterns)
    }

    @Test
    fun complete_afterRemovingPattern_persistsOnlyTheRemainder() = runTest {
        installService<PermissionManager>(FakePermissionManager(PermissionState.GRANTED))
        val viewModel = OnboardingPreferencesViewModel()

        viewModel.sendIntent(OnboardingPreferencesIntent.Load)
        advanceUntilIdle()
        val removed = LocalSettingsDefaults.defaultExecutionPatterns.first()
        viewModel.sendIntent(OnboardingPreferencesIntent.RemoveRule(removed))
        viewModel.sendIntent(OnboardingPreferencesIntent.Complete)
        advanceUntilIdle()

        val persisted = XRepo.executionRules.get(LocalSettingsDefaults.DEFAULT_EXECUTION_RULE_ID)
        assertEquals(
            LocalSettingsDefaults.defaultExecutionPatterns - removed,
            persisted?.patterns,
        )
    }

    private class FakePermissionManager(
        private val state: PermissionState,
    ) : PermissionManager {
        override fun status(permission: Permission): PermissionState = state
        override suspend fun request(permission: Permission): PermissionResult =
            PermissionResult(permission, state, emptyList())

        override suspend fun request(
            permission: Permission,
            vararg channels: Channel
        ): PermissionResult =
            PermissionResult(permission, state, emptyList())

        override fun applyScope(vararg channels: Channel): PermissionScope =
            object : PermissionScope {
                override suspend fun request(permission: Permission): PermissionResult =
                    PermissionResult(permission, state, emptyList())
            }
    }
}
