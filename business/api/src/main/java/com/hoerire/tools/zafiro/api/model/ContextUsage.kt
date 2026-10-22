package com.hoerire.tools.zafiro.api.model

/**
 * 当前会话的上下文占用：分子「已占用」与分母「上下文窗口」。
 *
 * - [usedTokens]：最近一次请求的输入总量，口径见 `Usage.contextTokens`（含缓存读写）；
 * - [windowTokens]：生效配置声明的窗口大小，是配置项，不是模型自报。
 *
 * 存在的理由：这一对值既是 Composer 上下文环的两个数，也是后续长对话自动压缩的
 * 触发依据（[progress]）。留在表现层，压缩就无处取数。
 */
data class ContextUsage(
    val usedTokens: Long,
    val windowTokens: Long,
    /** 可用预算 = 窗口 − 输出预留（maxTokens）。<=0 时退回窗口。 */
    val budgetTokens: Long = 0L,
    /**
     * 会话首个请求的 prompt 总量（系统提示 + 工具 schema + 技能清单 + 首条用户消息）。
     * 用来把 [usedTokens] 拆成「固定开销 + 本会话增量」；0 = 未知。
     */
    val baselineTokens: Long = 0L,
) {
    val effectiveBudget: Long
        get() = if (budgetTokens > 0L) budgetTokens else windowTokens

    /**
     * 占用比率，恒在 `0f..1f`。
     *
     * 窗口非法（≤0）时返回 0f：Float 的 NaN 参与比较恒为 false，
     * `coerceIn` 拦不住 `0f / 0` 得到的 NaN，所以先判分母。
     */
    val progress: Float
        get() = if (windowTokens > 0L) {
            // 口径 A：分母 = 总窗口（用户指定），预留只用于请求钳制，不进分母。
            (usedTokens.toFloat() / windowTokens).coerceIn(0f, 1f)
        } else {
            0f
        }
}
