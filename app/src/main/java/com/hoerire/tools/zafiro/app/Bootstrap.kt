package com.hoerire.tools.zafiro.app

import android.app.Activity

/**
 * 启动门槛已移除：不再申请 SU，也不会因为没有 SU 退出。
 * 环境目录仍固定为 /data/local/aitools，由外部流程负责部署。
 */
object Bootstrap {

    enum class Gate { EXIT, READY, SETUP }

    /** 恒定 READY：直接进主界面，不探测 SU、不跳部署页、不建目录。 */
    @Suppress("UNUSED_PARAMETER")
    fun gate(activity: Activity): Gate = Gate.READY
}
