package com.example.chatapp.features.chat.data.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.chatapp.R
import com.example.chatapp.core.Constants
import com.example.chatapp.features.chat.data.receiver.MessageSendCancelReceiver
import com.example.chatapp.features.chat.data.receiver.MessageSendRetryReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageSendNotifier @Inject constructor(
    @ApplicationContext val context: Context
) {

    fun showUploading(messageId: String, messageJson: String) {
        ensureNotificationChannel()
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            notificationId(messageId),
            Intent(context, MessageSendCancelReceiver::class.java)
                .putExtra(MessageSendCancelReceiver.EXTRA_MESSAGE_ID, messageId)
                .putExtra(MessageSendCancelReceiver.EXTRA_MESSAGE, messageJson),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notify(
            messageId,
            NotificationCompat.Builder(context, Constants.Notifications.MESSAGE_SEND_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(context.getString(R.string.chat_uploading_images))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, context.getString(R.string.chat_cancel), cancelIntent)
                .build()
        )
    }

    fun showFailed(messageId: String, messageJson: String) {
        ensureNotificationChannel()
        val retryIntent = PendingIntent.getBroadcast(
            context,
            notificationId(messageId),
            Intent(context, MessageSendRetryReceiver::class.java)
                .putExtra(MessageSendRetryReceiver.EXTRA_MESSAGE, messageJson),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notify(
            messageId,
            NotificationCompat.Builder(context, Constants.Notifications.MESSAGE_SEND_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.chat_upload_failed))
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.chat_retry), retryIntent)
                .build()
        )
    }

    fun dismiss(messageId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(messageId))
    }

    private fun ensureNotificationChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(
                Constants.Notifications.MESSAGE_SEND_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            ).setName(context.getString(R.string.notification_channel_message_send)).build()
        )
    }

    private fun notificationId(messageId: String) = messageId.hashCode()

    private fun notify(messageId: String, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) manager.notify(notificationId(messageId), notification)
    }
}
