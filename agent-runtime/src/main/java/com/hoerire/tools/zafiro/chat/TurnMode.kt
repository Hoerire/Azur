package com.hoerire.tools.zafiro.chat

sealed interface TurnMode {
    data object InjectedLLM : TurnMode
}
