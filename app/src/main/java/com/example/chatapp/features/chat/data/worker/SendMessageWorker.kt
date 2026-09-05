package com.example.chatapp.features.chat.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chatapp.features.chat.data.dto.MessageDto
import com.example.chatapp.features.chat.data.dto.toEntity
import com.example.chatapp.features.chat.domain.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json

@HiltWorker
class SendMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatRepository: ChatRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val message = inputData.getString(KEY_MESSAGE)
            ?.let { Json.decodeFromString(MessageDto.serializer(), it).toEntity() }
            ?: return Result.failure()

        return chatRepository.sendMessage(message)
            .fold(
                onSuccess = {
                    Result.success()
                },
                onFailure = {
                    Result.failure()
                }
            )
    }

    companion object {
        const val KEY_MESSAGE = "message"
        const val WORK_NAME_PREFIX = "send_message_"
    }
}
