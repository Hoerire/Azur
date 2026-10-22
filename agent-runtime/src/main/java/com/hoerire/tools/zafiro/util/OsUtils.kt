package com.hoerire.tools.zafiro.util

import android.os.Build

enum class OsFamily {
    ColorOS,
    HyperOS,
    Unknown,
}

object OsUtils {
    fun getCurr(): OsFamily {
        val manufacturer = Build.MANUFACTURER.trim().lowercase()
        return when {
            manufacturer in setOf("oppo", "oneplus", "realme") -> OsFamily.ColorOS
            manufacturer.contains("xiaomi") -> OsFamily.HyperOS
            else -> OsFamily.Unknown
        }
    }
}
