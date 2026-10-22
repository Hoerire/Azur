package com.hoerire.tools.zafiro.app.wakelock

enum class WakeDemand {
    /** 前台或后台有 Agent 会话处于运行态，且全局常亮开关已开启。 */
    AgentRunning,

    }

/** 该需求是否要求屏幕保持常亮。CPU 锁不在此列：任一需求存在即需要。 */
val WakeDemand.needsScreenOn: Boolean
    get() = this == WakeDemand.AgentRunning
