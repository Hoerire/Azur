package com.hoerire.tools.zafiro.app.ui.content

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.hoerire.tools.uikit.infra.component.SettingsGroupCard
import com.hoerire.tools.uikit.infra.component.SettingsItemDivider
import com.hoerire.tools.uikit.infra.component.SettingsListItem
import com.hoerire.tools.uikit.infra.component.SettingsListPageContent
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.repo.SharedHttp
import com.hoerire.tools.zafiro.repo.XRepo
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

private val DshBeijingZone: ZoneId = ZoneId.of("Asia/Shanghai")

/** 旧的按窗口比例阈值，保留常量避免历史引用报错。 */
private const val DshDefaultCompactionPercent = 70

/** 上下文超过该 Token 数时自动压缩（固定 100K）。 */
private const val DshDefaultCompactionTokens = 100_000L

private enum class UsageBalanceFailureKind {
    NoConfig,
    Request,
}

private sealed interface UsageBalanceState {
    data object Loading : UsageBalanceState

    data class Ready(
        val currency: String,
        val totalBalance: String,
        val grantedBalance: String,
        val toppedUpBalance: String,
        val configName: String,
        val modelName: String,
    ) : UsageBalanceState

    data class Failed(
        val kind: UsageBalanceFailureKind,
        val detail: String,
    ) : UsageBalanceState
}

private data class UsageTokenStats(
    val turns: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cacheReadTokens: Long,
    val cacheHitRatePercent: Double,
    val outputTokensPerSecond: Double,
    val conversationCount: Int,
    val messageCount: Int,
    // 「最近一轮」：按 user 消息切分，来自 DB，重启后仍在
    val lastTurnAtMs: Long = 0L,
    val lastTurnSteps: Int = 0,
    val lastTurnTokens: Long = 0L,
    val lastTurnTokensPerSecond: Double = 0.0,
    val lastTurnCacheHitPercent: Double = 0.0,
)

private data class BalancePayload(
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String,
)

private class BalanceHttpException(
    val statusCode: Int,
    val host: String,
) : Exception("HTTP " + statusCode)

/**
 * 余额接口候选地址：取 endpoint 的 scheme://host[:port] 再接 /user/balance，
 * 兼容带 /v1 前缀、只写基座或挂代理的场景；provider=deepseek 且 host 非官方域
 * 时再补一个官方地址兜底（此前按路径拼接导致部分配置 404）。
 */
private fun balanceUrlCandidates(endpoint: String, provider: String): List<String> {
    val origin = try {
        val uri = URI(endpoint.trim())
        val scheme = uri.scheme ?: return emptyList()
        val host = uri.host ?: return emptyList()
        val port = if (uri.port != -1) ":" + uri.port else ""
        scheme + "://" + host + port
    } catch (_: Exception) {
        return emptyList()
    }
    val candidates = mutableListOf(origin + "/user/balance", origin + "/v1/user/balance")
    if (provider.equals("deepseek", ignoreCase = true) &&
        !origin.contains("deepseek.com", ignoreCase = true)
    ) {
        candidates += "https://api.deepseek.com/user/balance"
    }
    return candidates.distinct()
}

private fun fetchBalanceOnce(url: String, apiKey: String): Result<BalancePayload> {
    return try {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build()
        val response = SharedHttp.client.newCall(request).execute()
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(BalanceHttpException(resp.code, URI(url).host.orEmpty()))
            }
            val json = JSONObject(body)
            val infos = json.optJSONArray("balance_infos")
            val first = if (infos != null && infos.length() > 0) infos.optJSONObject(0) else null
            if (first == null) {
                return Result.failure(IllegalStateException("empty balance_infos"))
            }
            Result.success(
                BalancePayload(
                    currency = first.optString("currency", "CNY"),
                    totalBalance = first.optString("total_balance", "--"),
                    grantedBalance = first.optString("granted_balance", "--"),
                    toppedUpBalance = first.optString("topped_up_balance", "--"),
                ),
            )
        }
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
}

private fun describeBalanceError(error: Throwable?): String = when (error) {
    is BalanceHttpException -> "HTTP " + error.statusCode + " · " + error.host
    null -> "request failed"
    else -> error.message.orEmpty().take(120).ifBlank { error.javaClass.simpleName }
}

private suspend fun loadUsageBalance(): UsageBalanceState {
    val document = try {
        XRepo.llmConfigs.document()
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        return UsageBalanceState.Failed(
            UsageBalanceFailureKind.Request,
            throwable.message.orEmpty().take(120),
        )
    }
    val config = document.activeConfig()
        ?: return UsageBalanceState.Failed(UsageBalanceFailureKind.NoConfig, "")
    val candidates = balanceUrlCandidates(config.endpoint, config.provider)
    if (candidates.isEmpty()) {
        return UsageBalanceState.Failed(
            UsageBalanceFailureKind.NoConfig,
            config.endpoint.take(80),
        )
    }
    var lastError: String = "request failed"
    for (url in candidates) {
        val result = withContext(Dispatchers.IO) { fetchBalanceOnce(url, config.apiKey) }
        val payload = result.getOrNull()
        if (payload != null) {
            return UsageBalanceState.Ready(
                currency = payload.currency,
                totalBalance = payload.totalBalance,
                grantedBalance = payload.grantedBalance,
                toppedUpBalance = payload.toppedUpBalance,
                configName = config.name,
                modelName = config.model,
            )
        }
        lastError = describeBalanceError(result.exceptionOrNull())
    }
    return UsageBalanceState.Failed(UsageBalanceFailureKind.Request, lastError)
}

/** DeepSeek 官方价（元 / 百万 token）：缓存命中输入 / 未命中输入 / 输出。仅用于本地估算。 */
private const val PriceCacheHitPerM = 0.2
private const val PriceMissPerM = 2.0
private const val PriceOutputPerM = 3.0

/** 今日消费估算：缓存命中走低价，其余输入按未命中价，输出单独计价。 */
private fun UsageTokenStats.estimatedTodayCost(): Double {
    val cached = cacheReadTokens.coerceAtLeast(0L).toDouble()
    val fresh = (inputTokens - cacheReadTokens).coerceAtLeast(0L).toDouble()
    return (
        cached * PriceCacheHitPerM +
            fresh * PriceMissPerM +
            outputTokens.coerceAtLeast(0L) * PriceOutputPerM
        ) / 1_000_000.0
}

private fun loadUsageTokenStats(context: Context): UsageTokenStats? {
    val databaseFile = context.getDatabasePath("conversation_history.db")
    if (!databaseFile.exists()) return null
    val database = SQLiteDatabase.openDatabase(
        databaseFile.path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    )
    return database.use { db ->
        val startOfDayMs = LocalDate.now(DshBeijingZone)
            .atStartOfDay(DshBeijingZone)
            .toInstant()
            .toEpochMilli()
        var turns = 0          // 用户消息条数（真正的「轮」）
        var missTokens = 0L    // 未命中缓存 prompt
        var inputTokens = 0L
        var outputTokens = 0L
        var reasoningTokens = 0L
        var cacheReadTokens = 0L
        var cacheWriteTokens = 0L
        var speedTokens = 0L
        var speedMillis = 0L
        // 当前轮（user 之后到下一次 user 之前）聚合 + 上一轮快照
        var tSteps = 0; var tTokens = 0L; var tHit = 0L; var tOut = 0L
        var tMs = 0L; var tFirstMs = 0L
        var lastAt = 0L; var lastSteps = 0; var lastTokens = 0L
        var lastTps = 0.0; var lastHitPct = 0.0
        var previousTs = -1L
        db.rawQuery(
            "SELECT timestamp, message_json FROM conversation_entry WHERE timestamp >= ? ORDER BY timestamp",
            arrayOf(startOfDayMs.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val entryTs = cursor.getLong(0)
                val messageJson = cursor.getString(1) ?: continue
                val deltaMs = if (previousTs > 0L) entryTs - previousTs else 0L
                previousTs = entryTs
                try {
                    val root = JSONObject(messageJson)
                    val type = root.optString("type")
                    // 「轮」= 用户消息条数；assistant 是每步一条，不能当轮
                    if (type.contains("User")) {
                        if (tSteps > 0) {
                            lastAt = tFirstMs; lastSteps = tSteps; lastTokens = tTokens
                            lastTps = if (tMs > 0L) tOut * 1000.0 / tMs else 0.0
                            lastHitPct = if (tTokens > 0L) tHit * 100.0 / tTokens else 0.0
                        }
                        tSteps = 0; tTokens = 0L; tHit = 0L; tOut = 0L; tMs = 0L; tFirstMs = 0L
                        turns += 1
                        continue
                    }
                    if (!type.contains("Assistant")) continue
                    val usage = root.optJSONObject("message")?.optJSONObject("usage") ?: continue
                    // inputTokens 是「未命中缓存」量，真实输入 = miss + 缓存读 + 缓存写
                    missTokens += usage.optLong("inputTokens")
                    outputTokens += usage.optLong("outputTokens")
                    reasoningTokens += usage.optLong("reasoningTokens")
                    cacheReadTokens += usage.optLong("cacheReadTokens")
                    cacheWriteTokens += usage.optLong("cacheWriteTokens")
                    // 生成速度：本轮输出 ÷ 与上一条记录的间隔（1~60 秒视为有效，排除思考/工具等待）
                    val output = usage.optLong("outputTokens")
                    if (output > 0L && deltaMs in 1_000L..60_000L) {
                        speedTokens += output
                        speedMillis += deltaMs
                    }
                    if (tSteps == 0) tFirstMs = entryTs
                    tSteps += 1
                    tTokens += usage.optLong("inputTokens") + usage.optLong("cacheReadTokens")
                    tHit += usage.optLong("cacheReadTokens")
                    tOut += output
                    if (deltaMs in 1_000L..60_000L) tMs += deltaMs
                } catch (_: JSONException) {
                    // 跳过无法解析的历史条目
                }
            }
        }
        val conversationCount = db.rawQuery("SELECT COUNT(*) FROM conversation", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        val messageCount = db.rawQuery("SELECT COUNT(*) FROM conversation_entry", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (tSteps > 0) {
            lastAt = tFirstMs; lastSteps = tSteps; lastTokens = tTokens
            lastTps = if (tMs > 0L) tOut * 1000.0 / tMs else 0.0
            lastHitPct = if (tTokens > 0L) tHit * 100.0 / tTokens else 0.0
        }
        inputTokens = missTokens + cacheReadTokens + cacheWriteTokens
        val cacheTotal = inputTokens
        val cacheHitRate = if (cacheTotal > 0L) cacheReadTokens * 100.0 / cacheTotal else 0.0
        val tokensPerSecond = if (speedMillis > 0L) speedTokens * 1000.0 / speedMillis else 0.0
        UsageTokenStats(
            turns = turns,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            reasoningTokens = reasoningTokens,
            cacheReadTokens = cacheReadTokens,
            cacheHitRatePercent = cacheHitRate,
            outputTokensPerSecond = tokensPerSecond,
            conversationCount = conversationCount,
            messageCount = messageCount,
            lastTurnAtMs = lastAt,
            lastTurnSteps = lastSteps,
            lastTurnTokens = lastTokens,
            lastTurnTokensPerSecond = lastTps,
            lastTurnCacheHitPercent = lastHitPct,
        )
    }
}

/** 分割线上压一行小字（今日消费）：线—字—线，样式与 SettingsItemDivider 对齐。 */
@Composable
private fun TodayCostDivider(label: String?) {
    if (label == null) {
        SettingsItemDivider()
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

/**
 * 用量行：右侧数值用 trailingContent 渲染，绕开 currentState 的 22% 宽度上限，
 * 数据再长也不会被省略号截断。
 */
@Composable
private fun UsageValueRow(title: String, value: String) {
    SettingsListItem(
        title = title,
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        },
    )
}

@Composable
fun UsagePanelContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dshScope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var balanceState by remember { mutableStateOf<UsageBalanceState>(UsageBalanceState.Loading) }
    var tokenStats by remember { mutableStateOf<UsageTokenStats?>(null) }
    var tokenStatsLoading by remember { mutableStateOf(true) }
    var peakSchedule by remember { mutableStateOf(DeepSeekPeakScheduleRepo.current()) }
    var periodNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { peakSchedule = DeepSeekPeakScheduleRepo.load(context) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            periodNowMs = System.currentTimeMillis()
        }
    }
    var autoCompressEnabled by remember { mutableStateOf(true) }
    var compactionTokens by remember { mutableIntStateOf(DshDefaultCompactionPercent) }
    var thresholdDialogVisible by remember { mutableStateOf(false) }
    var compactionSnapshots by remember { mutableStateOf<List<CompactionSnapshot>>(emptyList()) }
    var compactionExpanded by remember { mutableStateOf(false) }
    var summaryDialogVisible by remember { mutableStateOf(false) }
    var summaryDialogText by remember { mutableStateOf("") }

    LaunchedEffect(refreshKey) {
        balanceState = UsageBalanceState.Loading
        tokenStatsLoading = true
        balanceState = withContext(Dispatchers.IO) { loadUsageBalance() }
        tokenStats = withContext(Dispatchers.IO) {
            runCatching { loadUsageTokenStats(context) }.getOrNull()
        }
        compactionSnapshots = withContext(Dispatchers.IO) {
            runCatching { loadCompactions(context) }.getOrDefault(emptyList())
        }
        val savedPercent = runCatching { XRepo.contextCompactionThresholdPercent() }
            .getOrDefault(DshDefaultCompactionPercent)
        compactionTokens = savedPercent
        autoCompressEnabled = savedPercent > 0
        tokenStatsLoading = false
    }

    if (summaryDialogVisible) {
        AlertDialog(
            onDismissRequest = { summaryDialogVisible = false },
            title = { Text(stringResource(R.string.dsh_compress_state_dialog_title)) },
            text = {
                Text(
                    text = summaryDialogText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { summaryDialogVisible = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    SettingsListPageContent(
        description = stringResource(R.string.dsh_usage_description),
        modifier = modifier,
    ) {
        SettingsGroupCard(title = stringResource(R.string.dsh_whale_group_balance)) {
            when (val current = balanceState) {
                is UsageBalanceState.Loading -> {
                    SettingsListItem(
                        title = stringResource(R.string.dsh_whale_loading),
                        enabled = false,
                    )
                }

                is UsageBalanceState.Failed -> {
                    SettingsListItem(
                        title = stringResource(
                            when (current.kind) {
                                UsageBalanceFailureKind.NoConfig -> R.string.dsh_whale_err_no_config
                                UsageBalanceFailureKind.Request -> R.string.dsh_whale_err_request
                            },
                        ),
                        summary = current.detail.takeIf { it.isNotBlank() },
                        enabled = false,
                    )
                }

                is UsageBalanceState.Ready -> {
                    SettingsListItem(
                        title = stringResource(R.string.dsh_whale_balance_total),
                        trailingContent = {
                            Text(
                                text = current.totalBalance,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = current.currency,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    TodayCostDivider(
                        label = tokenStats?.let { stats ->
                            stringResource(
                                R.string.dsh_usage_today_cost,
                                String.format(java.util.Locale.US, "%.2f", stats.estimatedTodayCost()),
                            )
                        },
                    )
                    UsageValueRow(
                        title = stringResource(R.string.dsh_whale_balance_granted),
                        value = current.grantedBalance,
                    )
                    SettingsItemDivider()
                    UsageValueRow(
                        title = stringResource(R.string.dsh_whale_balance_topped),
                        value = current.toppedUpBalance,
                    )
                }
            }
        }

        SettingsGroupCard(title = stringResource(R.string.dsh_whale_group_period)) {
            val schedule = peakSchedule
            val offPeak = !schedule.isPeak(
                Instant.ofEpochMilli(periodNowMs),
                DshBeijingZone,
            )
            // 当前时段 / 高峰窗口 / 规则来源 合并成一条
            SettingsListItem(
                title = stringResource(R.string.dsh_whale_period_current),
                summary = stringResource(
                    R.string.dsh_whale_period_merged_fmt,
                    formatPeakWindows(schedule, DshBeijingZone),
                    stringResource(
                        if (schedule.source == PeakSchedule.SOURCE_DOC) {
                            R.string.dsh_whale_period_source_doc
                        } else {
                            R.string.dsh_whale_period_source_builtin
                        },
                    ),
                ),
                currentState = stringResource(
                    if (offPeak) R.string.dsh_whale_period_off else R.string.dsh_whale_period_peak,
                ),
            )
        }

        SettingsGroupCard(title = stringResource(R.string.dsh_bili_group_today)) {
            val current = tokenStats
            when {
                tokenStatsLoading -> {
                    SettingsListItem(
                        title = stringResource(R.string.dsh_bili_loading),
                        enabled = false,
                    )
                }

                current == null -> {
                    SettingsListItem(
                        title = stringResource(R.string.dsh_bili_error),
                        enabled = false,
                    )
                }

                else -> {
                    UsageValueRow(
                        title = stringResource(R.string.dsh_bili_turns),
                        value = current.turns.toString(),
                    )
                    SettingsItemDivider()
                    UsageValueRow(
                        title = stringResource(R.string.dsh_bili_input),
                        value = current.inputTokens.toString(),
                    )
                    SettingsItemDivider()
                    UsageValueRow(
                        title = stringResource(R.string.dsh_bili_output),
                        value = current.outputTokens.toString(),
                    )
                    SettingsItemDivider()
                    UsageValueRow(
                        title = stringResource(R.string.dsh_bili_reasoning),
                        value = current.reasoningTokens.toString(),
                    )
                    SettingsItemDivider()
                    UsageValueRow(
                        title = stringResource(R.string.dsh_bili_cache),
                        value = current.cacheReadTokens.toString(),
                    )
                    SettingsItemDivider()
                    UsageValueRow(
                        title = stringResource(R.string.dsh_bili_cache_hit),
                        value = String.format(
                            java.util.Locale.US,
                            "%.1f%%",
                            current.cacheHitRatePercent,
                        ),
                    )
                    SettingsItemDivider()
                    SettingsListItem(
                        title = stringResource(R.string.dsh_bili_speed),
                        // 走 trailingContent：避开 currentState 的最大宽度限制，避免 "tok/s" 被截断
                        trailingContent = {
                            Text(
                                text = String.format(
                                    java.util.Locale.US,
                                    "%.1f tok/s",
                                    current.outputTokensPerSecond,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }
        }

        // 自动压缩开关/阈值已移到「设置 → 运行控制」；这里只保留历史摘要。
        SettingsGroupCard(title = stringResource(R.string.dsh_compress_state_group)) {
            val snapshots = compactionSnapshots
            if (snapshots.isEmpty()) {
                SettingsListItem(
                    title = stringResource(R.string.dsh_compress_state_none),
                    enabled = false,
                )
            } else {
                SettingsListItem(
                    // 标题固定为「历史摘要」；记录条数只在展开后显示
                    title = stringResource(R.string.dsh_compress_state_group),
                    summary = if (compactionExpanded) {
                        stringResource(R.string.dsh_compress_state_count, snapshots.size)
                    } else {
                        null
                    },
                    showChevron = true,
                    onClick = { compactionExpanded = !compactionExpanded },
                )
                if (compactionExpanded) snapshots.forEachIndexed { index, snapshot ->
                    if (index > 0) SettingsItemDivider()
                    SettingsListItem(
                        title = compactionTimeLabel(snapshot.atMs),
                        currentState = if (snapshot.foldedTokens > 0L) "${snapshot.foldedTokens / 1000}k" else "-",
                        showChevron = true,
                        onClick = {
                            summaryDialogText = snapshot.summary
                            summaryDialogVisible = true
                        },
                    )
                }
            }
        }

        SettingsGroupCard(title = stringResource(R.string.dsh_bili_group_total)) {
            val current = tokenStats
            if (tokenStatsLoading || current == null) {
                SettingsListItem(
                    title = stringResource(
                        if (tokenStatsLoading) R.string.dsh_bili_loading else R.string.dsh_bili_error,
                    ),
                    enabled = false,
                )
            } else {
                UsageValueRow(
                    title = stringResource(R.string.dsh_bili_conversations),
                    value = current.conversationCount.toString(),
                )
                SettingsItemDivider()
                UsageValueRow(
                    title = stringResource(R.string.dsh_bili_messages),
                    value = current.messageCount.toString(),
                )
            }
        }



        SettingsGroupCard {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.dsh_usage_note_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.dsh_usage_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsGroupCard {
            SettingsListItem(
                title = stringResource(R.string.dsh_refresh),
                onClick = { refreshKey += 1 },
            )
        }
    }
}

/** 最近一次压缩检查点的快照。 */
private data class CompactionSnapshot(
    val generation: Int,
    val foldedTokens: Long,
    val summary: String,
    val atMs: Long,
)

private fun compactionTimeLabel(atMs: Long): String {
    if (atMs <= 0L) return "-"
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .let { fmt -> Instant.ofEpochMilli(atMs).atZone(DshBeijingZone).format(fmt) }
}

private const val UsageLogTag = "UsagePanel"

/** 读取最近一次上下文压缩检查点，供「用量」面板展示。 */
private fun loadCompactions(context: Context): List<CompactionSnapshot> {
    val databaseFile = context.getDatabasePath("conversation_history.db")
    if (!databaseFile.exists()) return emptyList()
    val database = openUsageDatabase(databaseFile.path) ?: return emptyList()
    return database.use { db ->
        val found = ArrayList<CompactionSnapshot>()
        var scanned = 0
        db.rawQuery(
            "SELECT message_json, timestamp FROM conversation_entry " +
                "WHERE message_json LIKE '%zfr-summary%' ORDER BY timestamp DESC LIMIT 400",
            null,
        ).use { cursor ->
            while (cursor.moveToNext() && found.size < 20) {
                scanned++
                val json = cursor.getString(0) ?: continue
                val parsed = parseCompactionSnapshot(json) ?: continue
                found.add(parsed.copy(atMs = cursor.getLong(1)))
            }
        }
        Log.i(UsageLogTag, "compaction: scanned=" + scanned + " found=" + found.size)
        found
    }
}

private fun openUsageDatabase(path: String): SQLiteDatabase? {
    return try {
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
    } catch (error: Exception) {
        Log.w(UsageLogTag, "compaction: read-only open failed, retry writable", error)
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (fallbackError: Exception) {
            Log.e(UsageLogTag, "compaction: open failed", fallbackError)
            null
        }
    }
}

private fun parseCompactionSnapshot(messageJson: String): CompactionSnapshot? {
    return try {
        val root = JSONObject(messageJson)
        // 压缩摘要只会以 User 消息的形式落库，先按类型过滤掉引用它的其它消息。
        if (!root.optString("type").endsWith("User")) return null
        val blocks = root.optJSONArray("content")
            ?: root.optJSONObject("message")?.optJSONArray("content")
            ?: return null
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            val text = block.optString("text")
            val trimmed = text.trimStart()
            if (!trimmed.startsWith("<zfr-summary>")) continue
            return parseSummaryBlock(text)
        }
        null
    } catch (error: Exception) {
        Log.w(UsageLogTag, "compaction: parse failed", error)
        null
    }
}

private fun parseSummaryBlock(text: String): CompactionSnapshot? {
    val open = text.indexOf("<summary>")
    if (open < 0) return null
    val summaryEnd = text.indexOf("</summary>")
    val wrapperEnd = text.indexOf("</zfr-summary>")
    val close = when {
        summaryEnd > open -> summaryEnd
        wrapperEnd > open -> wrapperEnd
        else -> text.length
    }
    val summary = text.substring(open + "<summary>".length, close).trim()
    fun intMeta(prefix: String): Int = text.lineSequence()
        .firstOrNull { it.trim().startsWith(prefix) }
        ?.trim()?.removePrefix(prefix)?.trim()?.toIntOrNull() ?: 1
    fun longMeta(prefix: String): Long = text.lineSequence()
        .firstOrNull { it.trim().startsWith(prefix) }
        ?.trim()?.removePrefix(prefix)?.trim()?.toLongOrNull() ?: 0L
    return CompactionSnapshot(
        generation = intMeta("generation:"),
        foldedTokens = longMeta("folded-tokens:"),
        summary = summary,
        atMs = 0L,
    )
}
