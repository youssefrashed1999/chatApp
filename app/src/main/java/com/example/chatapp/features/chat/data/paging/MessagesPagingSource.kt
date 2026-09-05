package com.example.chatapp.features.chat.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.chatapp.features.chat.data.datasource.ChatDatasource
import com.example.chatapp.features.chat.data.dto.toEntity
import com.example.chatapp.features.chat.domain.entity.Message
import kotlinx.datetime.Instant
import javax.inject.Inject

class MessagesPagingSource @Inject constructor(private val remoteDataSource: ChatDatasource) : PagingSource<String, Message>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Message> {
        return try {
            val cursor = params.key
            val page = remoteDataSource.getMessagesPage(before = cursor?.let { Instant.parse(it) }, pageSize = params.loadSize.toLong())
                .map { it.toEntity() }

            LoadResult.Page(
                data = page,
                prevKey = null,
                nextKey = if (page.isEmpty()) null else page.last().createdAt.toString()
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, Message>): String? = null
}
