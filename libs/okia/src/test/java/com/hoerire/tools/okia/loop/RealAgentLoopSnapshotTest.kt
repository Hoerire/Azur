package com.hoerire.tools.okia.loop

import com.hoerire.tools.okia.error.RetryPolicy
import com.hoerire.tools.okia.fake.FakeHttpEngine
import com.hoerire.tools.okia.fake.FakeProtocolMapper
import com.hoerire.tools.okia.fake.RecordingToolExecutor
import com.hoerire.tools.okia.fake.localTool
import com.hoerire.tools.okia.hooks.Hooks
import com.hoerire.tools.okia.hooks.ToolCallHolder
import com.hoerire.tools.okia.hooks.ToolResultHolder
import com.hoerire.tools.okia.message.ContentBlock
import com.hoerire.tools.okia.message.Message
import com.hoerire.tools.okia.message.StopReason
import com.hoerire.tools.okia.protocol.ProtocolEvent
import com.hoerire.tools.okia.protocol.RequestSnapshot
import com.hoerire.tools.okia.tooling.DefaultToolRegistry
import com.hoerire.tools.okia.transport.HttpTimeouts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RealAgentLoopSnapshotTest {

    @Test
    fun toolsAreReSnapshottedPerSegment() = runTest {
        val registry = DefaultToolRegistry()
        registry.register(localTool("tool-a"), RecordingToolExecutor())
        // 段间注册新工具：第一轮工具执行完成后（afterToolCall）注册 tool-b，
        // 第二轮 buildRequest 前完成——快照现取应看到它。
        val toolB = mutableListOf<String>()
        val hooks = listOf(object : Hooks {
            override suspend fun afterToolCall(call: ToolCallHolder, result: ToolResultHolder) {
                registry.register(localTool("tool-b"), RecordingToolExecutor())
                toolB += call.name
            }
        })

        // 第一轮：模型产出工具调用 A（无文本）；第二轮：Stop。
        val rounds = listOf(
            listOf(
                ProtocolEvent.ToolCallReady("call-a", "tool-a", "{}"),
                ProtocolEvent.Completed(stopReason = StopReason.ToolUse)
            ),
            listOf(
                ProtocolEvent.TextDelta("done"),
                ProtocolEvent.Completed(stopReason = StopReason.Stop)
            )
        )
        val mapper = FakeProtocolMapper(rounds)

        val request = LoopRequest(
            snapshot = RequestSnapshot(
                endpoint = "https://api.test/v1",
                apiKey = "k",
                model = "m",
                systemPrompt = null,
                temperature = 0.7f,
                maxTokens = 100,
                headers = emptyMap(),
                timeouts = HttpTimeouts(1_000, 1_000, 1_000),
                // send 时旧快照：tools 为空（G5 整改后每段覆盖，此处应被现取覆盖）
                tools = emptyList()
            ),
            history = listOf(Message.User(listOf(ContentBlock.Text("hi")))),
            input = "hi",
            options = LoopOptions(),
            idleTimeoutSeconds = null,
            toolRegistry = registry,
            protocolMapper = mapper,
            hooks = hooks,
            httpEngine = FakeHttpEngine(),
            retryPolicy = RetryPolicy(),
            onCommit = {}
        )

        val result = RealAgentLoop().run(request) { }
        assertTrue(result is TurnResult.Completed)
        assertTrue(toolB == listOf("tool-a")) // 工具 A 执行过，hook 注册了 tool-b

        // 两次 buildRequest（两轮段）：snapshot.tools 均为现取
        org.junit.Assert.assertEquals(2, mapper.builtSnapshots.size)
        // 第一段：snapshot.tools 初始为空，仍被现取覆盖为 [tool-a]
        val firstTools = mapper.builtSnapshots[0].tools.map { it.name }
        assertTrue("tool-a" in firstTools)
        assertTrue("tool-b" !in firstTools)
        // 第二段：段间注册的 tool-b 可见
        val secondTools = mapper.builtSnapshots[1].tools.map { it.name }
        assertTrue("tool-a" in secondTools)
        assertTrue("tool-b" in secondTools)
    }
}