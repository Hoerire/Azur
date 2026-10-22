package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 顶栏下的子页签：内容在原地切换，不做整页导航。 */
enum class DshSubTab { Chat, Usage, History, Settings }

object DshSubTabs {
    var current by mutableStateOf(DshSubTab.Chat)

    /** 同一项再点一次回到对话。 */
    fun toggle(tab: DshSubTab) {
        current = if (current == tab) DshSubTab.Chat else tab
    }
}
