package com.example.chatapp.features.chat.domain.repository

import androidx.paging.PagingData
import com.example.chatapp.features.chat.domain.entity.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface ChatRepository {
    fun observeMessageHistory(): Flow<PagingData<Message>>
    fun observeLiveMessages(): Flow<Message>
    suspend fun sendMessage(message: Message): Result<Unit>

    suspend fun refreshMessages(lastCreatedAt: Instant): List<Message>
}