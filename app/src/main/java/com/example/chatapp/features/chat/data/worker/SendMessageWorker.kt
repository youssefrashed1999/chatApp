package com.example.chatapp.features.chat.data.worker

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chatapp.core.Constants
import com.example.chatapp.core.Constants.Storage.PROFILE_IMAGE_EXTENSION
import com.example.chatapp.features.chat.data.dto.MessageDto
import com.example.chatapp.features.chat.data.dto.toEntity
import com.example.chatapp.features.chat.data.notification.MessageSendNotifier
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

@HiltWorker
class SendMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatRepository: ChatRepository,
    private val messageSendNotifier: MessageSendNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageJson = inputData.getString(KEY_MESSAGE) ?: return Result.failure()
        val message = runCatching {
            Json.decodeFromString(MessageDto.serializer(), messageJson).toEntity()
        }.getOrNull() ?: return Result.failure()

        val hasLocalMedia = message.content.mediaUrls.any { it.startsWith("content://") }
        if (hasLocalMedia) {
            messageSendNotifier.showUploading(message.id, messageJson)
        }
        val uploaded = try {
            uploadLocalMedia(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageSendNotifier.showFailed(message.id, messageJson)
            return Result.failure()
        }

        return chatRepository.sendMessage(uploaded)
            .fold(
                onSuccess = {
                    messageSendNotifier.dismiss(message.id)
                    Result.success()
                },
                onFailure = {
                    if (isStopped) throw CancellationException()
                    if (hasLocalMedia) {
                        messageSendNotifier.showFailed(message.id, messageJson)
                    }
                    Result.failure()
                }
            )

    }

    private suspend fun uploadLocalMedia(message: Message): Message {
        val urls = message.content.mediaUrls.mapIndexed { index, url ->
            val uri = url.toUri()
            val bytes = applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot read $url")
            val extension = applicationContext.contentResolver.getType(uri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: uri.lastPathSegment?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotBlank() }
                ?: PROFILE_IMAGE_EXTENSION
            chatRepository.uploadImage(
                Constants.getMessageMediaStoragePath(message.id, index, extension),
                bytes
            )
        }
        return message.copy(content = message.content.copy(mediaUrls = urls))
    }

    companion object {
        const val KEY_MESSAGE = "message"
        const val WORK_NAME_PREFIX = "send_message_"
    }
}
