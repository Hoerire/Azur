package com.hoerire.tools.zafiro.repo

import com.hoerire.tools.zafiro.api.util.tryIgnore
import com.hoerire.tools.zafiro.api.util.tryOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * 一次更新检查的结果。
 *
 * [hasUpdate] 为 true 时 [remoteVersion] 与 [releaseUrl] 一定非空；
 * [releaseNotes] 是 release 正文原文，可能为 null（弹窗只显示标题与提示行）。
 */
data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val remoteVersion: String?,
    val releaseUrl: String?,
    val releaseNotes: String?,
)

/**
 * 冷启动检测一次，结果通过 [result] 暴露给 UI。
 *
 * 同一版本只打扰一次：用户关掉弹窗后记为已提示，下次冷启动不再弹。
 */
object UpdateCheckHolder {
    private val _result = MutableStateFlow<UpdateCheckResult?>(null)
    val result: StateFlow<UpdateCheckResult?> = _result.asStateFlow()

    private var fired = false

    suspend fun runOnce(currentVersion: String) {
        if (fired) return
        fired = true
        val r = UpdateCheckApi.check(currentVersion)
        if (!r.hasUpdate) return
        if (r.remoteVersion != null && r.remoteVersion == XRepo.lastNotifiedUpdateVersion()) return
        _result.value = r
    }

    /** 关掉弹窗即记账，写盘失败只意味着下次可能再提醒一次。 */
    suspend fun dismiss() {
        val version = _result.value?.remoteVersion
        _result.value = null
        if (version != null) {
            withContext(Dispatchers.IO) {
                tryIgnore { XRepo.setLastNotifiedUpdateVersion(version) }
            }
        }
    }

    /** 供「关于」页手动触发；忽略「已提示过」的记账。 */
    suspend fun forceCheck(currentVersion: String): UpdateCheckResult {
        val r = UpdateCheckApi.check(currentVersion)
        if (r.hasUpdate) _result.value = r
        return r
    }
}

private object UpdateCheckApi {
    private val client = SharedHttp.client
    private val json = Json { ignoreUnknownKeys = true }

    private const val OWNER = "Hoerire"
    private const val REPO = "anvil"
    private const val GITHUB_API_LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val GITHUB_API_RECENT = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=1"

    private val semverRe = Regex("""(\d+)\.(\d+)\.(\d+)""")

    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        tryOrNull { resolve(currentVersion) } ?: noUpdate()
    }

    /** 先看最新 stable release；取不到时回落到最近一个 release（含 prerelease）。 */
    private fun resolve(currentVersion: String): UpdateCheckResult {
        checkRelease(fetch(GITHUB_API_LATEST), currentVersion)
            ?.let { return it }
        return checkRelease(fetchFirstOfList(GITHUB_API_RECENT), currentVersion) ?: noUpdate()
    }

    private fun fetch(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "$OWNER-$REPO")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun fetchFirstOfList(url: String): String? {
        val raw = fetch(url) ?: return null
        val array = tryOrNull { json.parseToJsonElement(raw) as JsonArray } ?: return null
        return (array.firstOrNull() as? JsonObject)?.toString()
    }

    private fun checkRelease(body: String?, currentVersion: String): UpdateCheckResult? {
        val root = tryOrNull { json.parseToJsonElement(body ?: return null) as JsonObject } ?: return null
        if (root["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
        val remoteVersion = (root["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: root["name"]?.jsonPrimitive?.contentOrNull)?.trim().orEmpty()
        if (remoteVersion.isEmpty()) return null
        val remoteUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val notes = root["body"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (!isNewer(remoteVersion, currentVersion)) return null
        return UpdateCheckResult(
            hasUpdate = true,
            remoteVersion = remoteVersion,
            releaseUrl = remoteUrl,
            releaseNotes = notes,
        )
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = semverRe.find(remote) ?: return false
        val c = semverRe.find(current) ?: return false
        for (i in 1..3) {
            val rp = r.groupValues[i].toIntOrNull() ?: 0
            val cp = c.groupValues[i].toIntOrNull() ?: 0
            if (rp > cp) return true
            if (rp < cp) return false
        }
        // 三段相同：远端带额外后缀（如 v1.0.1 与 1.0.1 相比）不算更新
        return false
    }

    private fun noUpdate() = UpdateCheckResult(
        hasUpdate = false,
        remoteVersion = null,
        releaseUrl = null,
        releaseNotes = null,
    )
}
