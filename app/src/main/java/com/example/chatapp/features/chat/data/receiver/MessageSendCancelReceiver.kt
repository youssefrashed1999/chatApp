package com.example.chatapp.features.chat.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chatapp.features.chat.data.notification.MessageSendNotifier
import com.example.chatapp.features.chat.domain.scheduler.MessageSendScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MessageSendCancelReceiver : BroadcastReceiver() {

    @Inject
    lateinit var messageSendScheduler: MessageSendScheduler

    @Inject
    lateinit var messageSendNotifier: MessageSendNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val messageJson = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        messageSendScheduler.cancel(messageId)
        messageSendNotifier.showFailed(messageId, messageJson)
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_MESSAGE = "extra_message"
    }
}
