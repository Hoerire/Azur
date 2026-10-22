package com.hoerire.tools.zafiro.runtime.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.net.toUri
import com.hoerire.tools.logging.Logger
import com.hoerire.tools.zafiro.api.Agent
import com.hoerire.tools.zafiro.api.AgentControl
import com.hoerire.tools.zafiro.api.AgentManager
import com.hoerire.tools.zafiro.api.TurnStart
import com.hoerire.tools.zafiro.api.model.AgentState
import com.hoerire.tools.zafiro.api.model.ApprovalDecision
import com.hoerire.tools.zafiro.app.notification.ResidentNotificationBuilder
import com.hoerire.tools.zafiro.app.notification.ResidentNotificationManager
import com.hoerire.tools.zafiro.business.notification.NotificationChannelManager
import com.hoerire.tools.zafiro.business.permission.PermissionManager
import com.hoerire.tools.zafiro.service.requireService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AgentRuntimeService : Service() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        val initialNotification = ResidentNotificationBuilder.build(
            context = this,
            channelManager = notificationChannelManager,
            status = agentControl.status.value,
        )
        startForeground(ResidentNotificationBuilder.NOTIFICATION_ID, initialNotification)
        observeStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ResidentNotificationBuilder.ACTION_STOP -> {
                Logger.i(LOG_TAG, "Resident notification: Stop clicked")
                agentControl.stop()
            }

            ResidentNotificationBuilder.ACTION_APPROVE -> {
                Logger.i(LOG_TAG, "Resident notification: Approve clicked")
                ResidentNotificationManager.resolveApproval(ApprovalDecision.Allow)
            }

            ResidentNotificationBuilder.ACTION_DECLINE -> {
                Logger.i(LOG_TAG, "Resident notification: Decline clicked")
                ResidentNotificationManager.resolveApproval(ApprovalDecision.Deny)
            }

            ACTION_START_RESIDENT -> {
                isResidentRequested = true
                updateResidentNotification()
            }

            ACTION_STOP_RESIDENT -> {
                isResidentRequested = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        statusJob?.cancel()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private val agentManager: AgentManager get() = requireService()
    private val agentControl: AgentControl get() = requireService()
    private val notificationChannelManager: NotificationChannelManager get() = requireService()
    private val permissionManager: PermissionManager get() = requireService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentHostAgent: Agent? = null
    private var statusJob: Job? = null
    private var boundClientsCount = 0
    private var isResidentRequested = false

    companion object {
        private const val LOG_TAG = "zafiro_AgentRuntimeService"
        const val ACTION_START_RESIDENT = "com.hoerire.tools.zafiro.action.START_RESIDENT"
        const val ACTION_STOP_RESIDENT = "com.hoerire.tools.zafiro.action.STOP_RESIDENT"
        private const val MAX_QUERY_LENGTH = 8192
        private const val STORE_CHANNEL_ID = "nexus_xservice_default_channel"
        private const val STORE_CHANNEL_NAME = "Zafiro"

        private var instance: AgentRuntimeService? = null

        fun notifyUpdate() {
            instance?.updateResidentNotification()
        }
    }

    fun updateResidentNotification() {
        val status = agentControl.status.value
        val notification = ResidentNotificationBuilder.build(
            context = this,
            channelManager = notificationChannelManager,
            status = status,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ResidentNotificationBuilder.NOTIFICATION_ID, notification)
    }

    private fun observeStatus() {
        statusJob = scope.launch {
            agentControl.status.collect {
                updateResidentNotification()
            }
        }
    }

        private fun createContentIntent(uri: String?): PendingIntent? {
            if (uri.isNullOrBlank()) {
                return null
            }
            val intent = Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val resolved =
                this@AgentRuntimeService.packageManager.resolveActivity(intent, 0) ?: return null
            val pendingIntentFlags =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(
                this@AgentRuntimeService,
                resolved.activityInfo.packageName.hashCode(),
                intent,
                pendingIntentFlags
            )
        }

        private fun resolveSmallIcon(): Int {
            return this@AgentRuntimeService.applicationInfo.icon.takeIf { it != 0 }
                ?: android.R.drawable.ic_dialog_info
        }

        private fun notificationId(title: String, content: String): Int {
            var result = title.hashCode()
            result = 31 * result + content.hashCode()
            return result
        }
    }

private const val PREEMPT_TAG = "zafiro_AgentRuntimeService"
private const val PREEMPT_MAX_ATTEMPTS = 3
private const val PREEMPT_STOP_TIMEOUT_MS = 10_000L

/**
 * 停止正在运行的回合并等它真正落地，再重新发起；有界重试兜住 stop 的异步落地窗口。
 *
 * 单独提出是为了可在不依赖 Service 的情况下验证这条重试/取消契约。
 *
 * @return 新回合是否已开始。false = 已耗尽重试或草稿为空，调用方应发回忙错误。
 */
internal suspend fun preemptPreviousTurn(
    agent: Agent,
    maxAttempts: Int = PREEMPT_MAX_ATTEMPTS,
    stopTimeoutMs: Long = PREEMPT_STOP_TIMEOUT_MS,
): Boolean {
    repeat(maxAttempts) { attempt ->
        val stopStartMs = System.currentTimeMillis()
        agent.stop()
        val stopped = withTimeoutOrNull(stopTimeoutMs) {
            agent.status.first { it is AgentState.Idle }
        }
        val stopWaitMs = System.currentTimeMillis() - stopStartMs
        if (stopped == null) {
            Logger.w(
                PREEMPT_TAG,
                "preempt stop timed out attempt=${attempt + 1} stopWaitMs=$stopWaitMs"
            )
            return@repeat
        }
        val streamStartMs = System.currentTimeMillis()
        val result = agent.stream()
        Logger.i(
            PREEMPT_TAG,
            "preempt stage attempt=${attempt + 1} stopWaitMs=$stopWaitMs " +
                    "streamCallMs=${System.currentTimeMillis() - streamStartMs} result=$result"
        )
        when (result) {
            TurnStart.Started -> {
                Logger.i(PREEMPT_TAG, "preempt restart started attempt=${attempt + 1}")
                return true
            }

            TurnStart.Busy -> Logger.w(
                PREEMPT_TAG,
                "preempt restart still busy attempt=${attempt + 1}/$maxAttempts"
            )

            TurnStart.DraftEmpty -> {
                Logger.w(PREEMPT_TAG, "preempt restart draftEmpty=true")
                return false
            }
        }
    }
    Logger.w(PREEMPT_TAG, "preempt exhausted attempts=$maxAttempts")
    return false
}