package com.example.chatapp.features.chat.domain.usecase

import com.example.chatapp.core.session.CurrentUserProvider
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.entity.MessageContent
import com.example.chatapp.features.chat.domain.entity.SendStatus
import com.example.chatapp.features.chat.domain.repository.ChatRepository
import com.example.chatapp.features.chat.domain.scheduler.MessageSendScheduler
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val scheduler: MessageSendScheduler,
) {
    operator fun invoke(message: Message) = scheduler.schedule(message)
}