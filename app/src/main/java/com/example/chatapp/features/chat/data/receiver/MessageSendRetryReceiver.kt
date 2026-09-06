package com.example.chatapp.features.chat.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chatapp.features.chat.data.dto.MessageDto
import com.example.chatapp.features.chat.data.dto.toEntity
import com.example.chatapp.features.chat.data.notification.MessageSendNotifier
import com.example.chatapp.features.chat.domain.scheduler.MessageSendScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.serialization.json.Json

@AndroidEntryPoint
class MessageSendRetryReceiver : BroadcastReceiver() {

    @Inject
    lateinit var messageSendScheduler: MessageSendScheduler

    @Inject
    lateinit var messageSendNotifier: MessageSendNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val messageJson = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val message = runCatching {
            Json.decodeFromString(MessageDto.serializer(), messageJson).toEntity()
        }.getOrNull() ?: return

        messageSendScheduler.schedule(message)
        messageSendNotifier.dismiss(message.id)
    }

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
    }
}
