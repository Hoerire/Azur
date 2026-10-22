package com.hoerire.tools.zafiro.util

import android.app.KeyguardManager
import android.os.UserManager
import kotlinx.coroutines.withTimeoutOrNull

object LockState {
    suspend fun isUnlocked(): Boolean = withTimeoutOrNull(1000L) {
        val context = AppContext.await()
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            ?: return@withTimeoutOrNull false
        val userManager = context.getSystemService(UserManager::class.java)
            ?: return@withTimeoutOrNull false
        if (!userManager.isUserUnlocked) return@withTimeoutOrNull false
        !keyguardManager.isDeviceLocked
    } ?: false
}
