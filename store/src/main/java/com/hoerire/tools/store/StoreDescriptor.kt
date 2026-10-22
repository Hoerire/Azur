package com.hoerire.tools.store

data class StoreDescriptor(
    val id: String,
    val relativePath: String,
    val defaultJson: String = "{}"
)
