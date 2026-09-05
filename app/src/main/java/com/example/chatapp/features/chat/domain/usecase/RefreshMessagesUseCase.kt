package com.example.chatapp.features.chat.domain.usecase

import com.example.chatapp.features.chat.domain.repository.ChatRepository
import kotlinx.datetime.Instant
import javax.inject.Inject

class RefreshMessagesUseCase @Inject constructor(private val repository: ChatRepository) {
    suspend operator fun invoke(lastCreatedAt: Instant) = repository.refreshMessages(lastCreatedAt)
}