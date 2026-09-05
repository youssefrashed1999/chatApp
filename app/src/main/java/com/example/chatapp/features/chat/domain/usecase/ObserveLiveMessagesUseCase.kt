package com.example.chatapp.features.chat.domain.usecase

import com.example.chatapp.features.chat.domain.repository.ChatRepository
import javax.inject.Inject

class ObserveLiveMessagesUseCase @Inject constructor(private val repository: ChatRepository) {
    operator fun invoke() = repository.observeLiveMessages()
}