package com.hoerire.tools.zafiro.repo

import android.content.Context
import android.content.ContextWrapper
import com.hoerire.tools.store.StoreDescriptorRegistry
import com.hoerire.tools.zafiro.app.util.SilentLoggerRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.hoerire.tools.zafiro.settings.model.RuntimeAgentMemoryMode as AgentMemoryMode
import com.hoerire.tools.zafiro.settings.model.RuntimeAgentProfile as AgentProfile

class XRepoDomainSettingsTest {
    @get:Rule
    val silentLoggerRule = SilentLoggerRule()

    private val context: Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.hoerire.tools.zafiro"
    }

    @After
    fun tearDown() {
        XRepo.resetForTest()
    }

    @Test
    fun appStateTogglePersistsAndKeepsOtherFields() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        XRepo.setOnboardingCompleted(true)
        XRepo.setLoadLastConversationOnStartup(false)
        XRepo.setLanguageTag("zh-CN")

        assertFalse(XRepo.loadLastConversationOnStartup())
        assertEquals("zh-CN", XRepo.languageTag())
        assertTrue(XRepo.onboardingCompleted())

    }

    @Test
    fun draftText_persistsAndRecoversDefault() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        assertEquals("", XRepo.draftText())
        XRepo.setDraftText("my global draft")
        assertEquals("my global draft", XRepo.draftText())
    }

    @Test
    fun setConversationPinned_addRemoveAndRepinKeepsLatestTimestamp() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        XRepo.setConversationPinned("c1", pinned = true, now = 100L)
        XRepo.setConversationPinned("c2", pinned = true, now = 200L)
        assertEquals(
            listOf(PinnedConversation("c1", 100L), PinnedConversation("c2", 200L)),
            XRepo.pinnedConversations(),
        )

        // 重复置顶同一会话：更新时刻，不产生重复项
        XRepo.setConversationPinned("c1", pinned = true, now = 300L)
        assertEquals(
            listOf(PinnedConversation("c2", 200L), PinnedConversation("c1", 300L)),
            XRepo.pinnedConversations(),
        )

        XRepo.setConversationPinned("c1", pinned = false)
        assertEquals(listOf(PinnedConversation("c2", 200L)), XRepo.pinnedConversations())
    }

    @Test
    fun hydrateSettingsBackfillsAllReactiveFlows() = runTest {
        val store = FakeDomainSettingsStore(
            StoreDescriptorRegistry.APP_STATE_ID to
                    """{"keep_screen_on":false,"always_show_message_actions":false,"floating_ball_enabled":true,"resident_notification_enabled":true,"floating_ball_auto_expand":false}""",
        )
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        // flow 初值是猜的默认值，与盘上真值相反，hydrate 后必须对齐真值。
        assertTrue(XRepo.keepScreenOnSetting.value)
        assertTrue(XRepo.alwaysShowMessageActionsSetting.value)
        assertFalse(XRepo.floatingBallEnabledSetting.value)
        assertFalse(XRepo.residentNotificationEnabledSetting.value)
        assertTrue(XRepo.floatingBallAutoExpandSetting.value)

        XRepo.hydrateSettings()

        assertFalse(XRepo.keepScreenOnSetting.value)
        assertFalse(XRepo.alwaysShowMessageActionsSetting.value)
        assertTrue(XRepo.floatingBallEnabledSetting.value)
        assertTrue(XRepo.residentNotificationEnabledSetting.value)
        assertFalse(XRepo.floatingBallAutoExpandSetting.value)
    }

    @Test
    fun reactiveGetterBackfillsFlowOnRead() = runTest {
        val store = FakeDomainSettingsStore(
            StoreDescriptorRegistry.APP_STATE_ID to """{"floating_ball_enabled":true}""",
        )
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        assertFalse(XRepo.floatingBallEnabledSetting.value)
        assertTrue(XRepo.floatingBallEnabled())
        assertTrue(XRepo.floatingBallEnabledSetting.value)
    }

    @Test
    fun reactiveSetterSyncsFlowBeforeDiskWrite() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        XRepo.setKeepScreenOn(false)

        // flow 同步回显，盘上同样落盘。
        assertFalse(XRepo.keepScreenOnSetting.value)
        assertFalse(XRepo.keepScreenOn())
    }

    @Test
    fun stringSettingsTrimOnWrite() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        XRepo.setLanguageTag("  zh-CN  ")
        XRepo.setLastOpenedConversationId("  conv-1  ")

        assertEquals("zh-CN", XRepo.languageTag())
        assertEquals("conv-1", XRepo.lastOpenedConversationId())
    }

    @Test
    fun llmConfigs_upsertBlankNameFallsBackToProvider() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        XRepo.llmConfigs.upsert(savedConfig(id = "cfg-1", model = "deepseek-chat").copy(name = " "))

        assertEquals("deepseek", XRepo.llmConfigs.active()?.name)
    }

    @Test
    fun activeSavedConfigReadsAndWritesOnlyLlmConfigsStore() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        assertNull(XRepo.llmConfigs.active())
        XRepo.llmConfigs.upsert(savedConfig(id = "cfg-1", model = "gpt-test"))

        assertEquals(
            listOf(StoreDescriptorRegistry.LLM_CONFIGS_ID),
            store.writeIds.distinct(),
        )
        assertEquals("cfg-1", XRepo.llmConfigs.active()?.id)
        assertEquals("gpt-test", XRepo.llmConfigs.active()?.model)
    }

    private fun savedConfig(
        id: String,
        model: String,
    ): SavedLlmConfig {
        return SavedLlmConfig(
            id = id,
            name = id,
            provider = "deepseek",
            endpoint = "https://api.deepseek.com/chat/completions",
            apiKey = "secret",
            model = model,
            protocol = "openai-responses",
            proxy = "",
        )
    }

    @Test
    fun agentConfigReadsAndWritesOnlyExistingEnabledAgents() = runTest {
        val enabledAgent = AgentProfile(
            id = "agent_a",
            name = "Agent A",
            alias = "agent_a",
            enabled = true,
            memoryMode = AgentMemoryMode.SharedMain,
        )
        val disabledAgent = AgentProfile(
            id = "agent_b",
            name = "Agent B",
            alias = "agent_b",
            enabled = false,
            memoryMode = AgentMemoryMode.SharedMain,
        )
        val store = FakeDomainSettingsStore(
            StoreDescriptorRegistry.AGENT_REGISTRY_ID to AgentSettingsCodec.encodeRegistry(
                listOf(enabledAgent, disabledAgent)
            ),
            StoreDescriptorRegistry.AGENT_MAIN_MEMORY_ID to MemorySettingsCodec.encodeMemories(
                listOf("Fact"),
                nowMillis = 1L,
            ),
        )
        XRepo.installStoreForTest(store)
        XRepo.init(context)
        assertEquals(listOf("Fact"), XRepo.agents.memoriesFor("agent_a"))
        assertEquals(emptyList<String>(), XRepo.agents.memoriesFor("agent_b"))
    }

    @Test
    fun memoryWritesOnlyAgentMainMemoryStore() = runTest {
        val store = FakeDomainSettingsStore()
        XRepo.installStoreForTest(store)
        XRepo.init(context)

        XRepo.memory.replaceAll(listOf(" A ", " ", "B"))

        assertEquals(listOf(StoreDescriptorRegistry.AGENT_MAIN_MEMORY_ID), store.writeIds)
        assertEquals(
            listOf("A", "B"), MemorySettingsCodec.parseMemories(
                store.jsonFor(
                    StoreDescriptorRegistry.AGENT_MAIN_MEMORY_ID
                )
            )
        )
    }
}
