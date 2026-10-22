package com.hoerire.tools.zafiro.chat.agentic.device

import com.hoerire.tools.zafiro.util.AppContext
import kotlinx.coroutines.CompletableDeferred

/** 进程级应用信息缓存（原继承 xposed-api 的 XProvider，现已内联）。 */
object AppInfoProvider {
    private val cache = CompletableDeferred<AppInfoCache>()

    @Volatile
    private var installed = false

    suspend fun cache(): AppInfoCache {
        if (!installed) {
            val context = AppContext.await().applicationContext
            if (!cache.isCompleted) cache.complete(AppInfoCache(context))
            installed = true
        }
        return cache.await()
    }
}
