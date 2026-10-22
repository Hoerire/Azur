package com.hoerire.tools.zafiro.settings

sealed class MemoryMutationResult {
    data object Ok : MemoryMutationResult()
    data object NotFound : MemoryMutationResult()
    data object Ambiguous : MemoryMutationResult()
}
