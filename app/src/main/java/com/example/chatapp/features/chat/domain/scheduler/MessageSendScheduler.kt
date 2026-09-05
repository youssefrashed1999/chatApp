package com.example.chatapp.features.chat.domain.scheduler

import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.entity.SendStatus
import kotlinx.coroutines.flow.Flow

interface MessageSendScheduler {
    fun schedule(message: Message)
    fun cancel(messageId: String)
    fun observeStatus(messageId: String): Flow<SendStatus>
}