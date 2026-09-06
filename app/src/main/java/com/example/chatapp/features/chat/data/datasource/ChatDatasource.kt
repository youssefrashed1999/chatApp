package com.example.chatapp.features.chat.data.datasource

import com.example.chatapp.core.Constants.Database.CREATED_AT_COLUMN
import com.example.chatapp.core.Constants.Database.MESSAGES_INSERTS_CHANNEL
import com.example.chatapp.core.Constants.Database.MESSAGES_TABLE
import com.example.chatapp.core.Constants.Database.PUBLIC_SCHEMA
import com.example.chatapp.core.Constants.Storage.BUCKET
import com.example.chatapp.features.chat.data.dto.MessageDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Instant
import javax.inject.Inject

class ChatDatasource @Inject constructor(private val supabaseClient: SupabaseClient) {

    suspend fun getMessagesPage(before: Instant?, pageSize: Long): List<MessageDto> =
        supabaseClient.from(MESSAGES_TABLE).select {
            order(CREATED_AT_COLUMN, Order.DESCENDING)
            before?.let { filter { lt(CREATED_AT_COLUMN, it.toString()) } }
            limit(pageSize)
        }.decodeList<MessageDto>()

    suspend fun refreshMessages(lastCreatedAt: Instant): List<MessageDto> =
        supabaseClient.from(MESSAGES_TABLE).select {
            order(CREATED_AT_COLUMN, Order.ASCENDING)
            filter { gt(CREATED_AT_COLUMN, lastCreatedAt.toString()) }
        }.decodeList<MessageDto>()

    suspend fun sendMessage(dto: MessageDto) {
        supabaseClient.from(MESSAGES_TABLE).insert(dto)
    }

    fun observeMessageInserts(): Flow<MessageDto> {
        val channel = supabaseClient.channel(MESSAGES_INSERTS_CHANNEL)

        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = PUBLIC_SCHEMA) {
            table = MESSAGES_TABLE
        }.map { it.decodeRecord<MessageDto>() }

        return inserts.onStart {
            channel.subscribe()
        }
    }

    suspend fun uploadImage(path: String, bytes: ByteArray): String {
        supabaseClient.storage.from(BUCKET).upload(path, bytes) { upsert = true }
        return supabaseClient.storage.from(BUCKET).publicUrl(path)
    }
}