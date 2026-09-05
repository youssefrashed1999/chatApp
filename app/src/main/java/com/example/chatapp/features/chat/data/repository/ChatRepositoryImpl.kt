package com.example.chatapp.features.chat.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.chatapp.core.Constants.PAGE_SIZE
import com.example.chatapp.features.chat.data.datasource.ChatDatasource
import com.example.chatapp.features.chat.data.dto.toDto
import com.example.chatapp.features.chat.data.dto.toEntity
import com.example.chatapp.features.chat.data.paging.MessagesPagingSource
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val remoteDataSource: ChatDatasource
) : ChatRepository {

    override fun observeMessageHistory(): Flow<PagingData<Message>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { MessagesPagingSource(remoteDataSource) }
        ).flow

    override fun observeLiveMessages(): Flow<Message> = remoteDataSource.observeMessageInserts()
        .map { dto -> dto.toEntity() }

    override suspend fun sendMessage(message: Message): Result<Unit> = runCatching {
        remoteDataSource.sendMessage(message.toDto())
    }

    override suspend fun refreshMessages(lastCreatedAt: Instant): List<Message> {
        return remoteDataSource.refreshMessages(lastCreatedAt).map { it.toEntity() }
    }
}
