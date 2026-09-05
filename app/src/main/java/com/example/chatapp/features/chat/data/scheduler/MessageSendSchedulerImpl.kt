package com.example.chatapp.features.chat.data.scheduler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.chatapp.features.chat.data.dto.MessageDto
import com.example.chatapp.features.chat.data.dto.toDto
import com.example.chatapp.features.chat.data.worker.SendMessageWorker
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.entity.SendStatus
import com.example.chatapp.features.chat.domain.scheduler.MessageSendScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class MessageSendSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MessageSendScheduler {

    override fun schedule(message: Message) {
        val request = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setInputData(
                workDataOf(
                    SendMessageWorker.KEY_MESSAGE to
                            Json.encodeToString(MessageDto.serializer(), message.toDto())
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SendMessageWorker.WORK_NAME_PREFIX + message.id,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancel(messageId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(SendMessageWorker.WORK_NAME_PREFIX + messageId)
    }

    override fun observeStatus(messageId: String): Flow<SendStatus> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SendMessageWorker.WORK_NAME_PREFIX + messageId)
            .map { infos -> infos.firstOrNull()?.state.toSendStatus() }

    private fun WorkInfo.State?.toSendStatus(): SendStatus = when (this) {
        WorkInfo.State.SUCCEEDED -> SendStatus.SENT
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> SendStatus.FAILED
        else -> SendStatus.SENDING
    }
}
