package com.hoerire.tools.zafiro.runtime

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.hoerire.tools.zafiro.util.AppContext
import com.hoerire.tools.zafiro.business.notification.AppNotificationChannel
import com.hoerire.tools.zafiro.business.notification.NotificationChannelManager
import com.hoerire.tools.zafiro.service.requireService
import com.hoerire.tools.zafiro.settings.RuntimeHostGateway

class IpcRuntimeHostGateway : RuntimeHostGateway {
    override suspend fun postNotification(
        title: String,
        content: String,
        uri: String?,
    ): Boolean {
        val context = AppContext.await()
        val notiManager = requireService<NotificationChannelManager>()
        val id = notificationId(title, content, uri)
        return notiManager.post(AppNotificationChannel.Alerts, id) {
            val icon = context.applicationInfo.icon.takeIf { it != 0 }
                ?: android.R.drawable.ic_dialog_info
            setSmallIcon(icon)
            setContentTitle(title)
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(content))
            setPriority(NotificationCompat.PRIORITY_MAX)
            setDefaults(NotificationCompat.DEFAULT_ALL)
            setAutoCancel(true)
            createContentIntent(context, uri)?.let { setContentIntent(it) }
        }
    }

    private fun createContentIntent(context: Context, uri: String?): PendingIntent? {
        if (uri.isNullOrBlank()) {
            return null
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val resolved = context.packageManager.resolveActivity(intent, 0) ?: return null
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            resolved.activityInfo.packageName.hashCode(),
            intent,
            pendingIntentFlags
        )
    }

    private fun notificationId(title: String, content: String, uri: String?): Int {
        var result = title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (uri?.hashCode() ?: 0)
        return result
    }
}
