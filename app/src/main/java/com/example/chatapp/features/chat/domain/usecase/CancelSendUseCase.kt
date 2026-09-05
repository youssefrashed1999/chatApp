package com.example.chatapp.features.chat.domain.usecase

import com.example.chatapp.features.chat.domain.scheduler.MessageSendScheduler
import javax.inject.Inject

class CancelSendUseCase @Inject constructor(
    private val scheduler: MessageSendScheduler
) {
    operator fun invoke(messageId: String) = scheduler.cancel(messageId)
}