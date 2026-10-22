package com.hoerire.tools.zafiro.util

import android.content.Context
import kotlinx.coroutines.CompletableDeferred

/**
 * 应用级 Context 单例（原 xposed-api 的 ContextProvider）。
 *
 * 由 App.onCreate 调 [provide]；运行期任何线程用 [await] 取，不依赖 Xposed 注入。
 */
object AppContext {
    private var deferred = CompletableDeferred<Context>()

    fun provide(context: Context) {
        if (!deferred.isCompleted) deferred.complete(context)
    }

    suspend fun await(): Context = deferred.await()

    /** 非挂起快照读：未 provide 时返回 null，供无法 suspend 的同步路径使用。 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun awaitIfAvailable(): Context? =
        if (deferred.isCompleted) deferred.getCompleted() else null

    fun clearForTest() {
        deferred = CompletableDeferred()
    }
}
