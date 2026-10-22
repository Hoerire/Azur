package com.hoerire.tools.okia.transport

data class SseEvent(
    val data: String,
    val event: String? = null
)
