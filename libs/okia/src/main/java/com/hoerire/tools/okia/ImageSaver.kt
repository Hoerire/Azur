package com.hoerire.tools.okia

fun interface ImageSaver {
    suspend fun save(base64: String): String?
}
