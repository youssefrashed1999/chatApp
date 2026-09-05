package com.example.chatapp.features.chat.domain.usecase

import androidx.paging.PagingData
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessagesUseCase @Inject constructor(private val repository: ChatRepository) {
    operator fun invoke(): Flow<PagingData<Message>> = repository.observeMessageHistory()
}