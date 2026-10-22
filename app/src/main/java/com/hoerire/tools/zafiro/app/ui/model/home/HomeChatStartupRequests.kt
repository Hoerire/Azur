package com.hoerire.tools.zafiro.app.ui.model.home

/**
 * 跨页「启动后立刻新建对话」请求。
 *
 * 历史页点「新建对话」→ 打标记 → 清栈回首页；HomeChatViewModel 的
 * restoreStartupState() 是异步恢复上次会话的，若在它之前发 NewConversation
 * 会被恢复流程覆盖，所以请求由 VM 在恢复结束后自己消费。
 */
object HomeChatStartupRequests {
    @Volatile
    private var pendingNewConversation = false

    fun requestNewConversation() {
        pendingNewConversation = true
    }

    fun consumeNewConversation(): Boolean {
        val v = pendingNewConversation
        pendingNewConversation = false
        return v
    }
}
