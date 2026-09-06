package com.example.chatapp.features.chat.data.worker

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.chatapp.features.chat.data.dto.MessageDto
import com.example.chatapp.features.chat.data.notification.MessageSendNotifier
import com.example.chatapp.features.chat.domain.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendMessageWorkerTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val params = mockk<WorkerParameters>()
    private val chatRepository = mockk<ChatRepository>()
    private val notifier = mockk<MessageSendNotifier>(relaxed = true)

    private val worker by lazy {
        SendMessageWorker(context, params, chatRepository, notifier)
    }

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { context.contentResolver } returns contentResolver
    }

    @After
    fun tearDown() = unmockkAll()

    private fun messageJson(dto: MessageDto): String =
        Json.encodeToString(MessageDto.serializer(), dto)

    private fun textDto() = MessageDto(
        id = "m1",
        deviceId = "d1",
        mediaType = "text",
        content = "hello",
        mediaUrls = emptyList(),
        createdAt = "2026-01-01T10:00:00Z"
    )

    private fun imageDto() = MessageDto(
        id = "m1",
        deviceId = "d1",
        mediaType = "image",
        content = null,
        mediaUrls = listOf("content://media/1"),
        createdAt = "2026-01-01T10:00:00Z"
    )

    private fun givenInput(json: String) {
        every { params.inputData } returns workDataOf(SendMessageWorker.KEY_MESSAGE to json)
    }

    private fun givenLocalMediaReadable() {
        val uri = mockk<Uri>()
        every { Uri.parse("content://media/1") } returns uri
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream("img".toByteArray())
        every { contentResolver.getType(uri) } returns null
    }

    @Test
    fun `missing message input returns failure`() = runTest {
        every { params.inputData } returns Data.EMPTY

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { chatRepository.sendMessage(any()) }
    }

    @Test
    fun `invalid message json returns failure`() = runTest {
        givenInput("not valid json")

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { chatRepository.sendMessage(any()) }
    }

    @Test
    fun `text message sends without upload notification`() = runTest {
        val json = messageJson(textDto())
        givenInput(json)
        coEvery { chatRepository.sendMessage(any()) } returns Result.success(Unit)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { chatRepository.sendMessage(match { it.id == "m1" }) }
        verify(exactly = 0) { notifier.showUploading(any(), any()) }
        verify { notifier.dismiss("m1") }
    }

    @Test
    fun `text message send failure returns failure without failed notification`() = runTest {
        givenInput(messageJson(textDto()))
        coEvery { chatRepository.sendMessage(any()) } returns Result.failure(IOException())

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        verify(exactly = 0) { notifier.showFailed(any(), any()) }
    }

    @Test
    fun `image message uploads media then sends with remote urls`() = runTest {
        val json = messageJson(imageDto())
        givenInput(json)
        givenLocalMediaReadable()
        coEvery { chatRepository.uploadImage("messages/m1/0.jpg", any()) } returns "https://cdn/m1.jpg"
        coEvery { chatRepository.sendMessage(any()) } returns Result.success(Unit)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify { notifier.showUploading("m1", json) }
        coVerify {
            chatRepository.uploadImage(
                "messages/m1/0.jpg",
                match { it.contentEquals("img".toByteArray()) }
            )
        }
        coVerify {
            chatRepository.sendMessage(match { it.content.mediaUrls == listOf("https://cdn/m1.jpg") })
        }
        verify { notifier.dismiss("m1") }
    }

    @Test
    fun `unreadable local media shows failed notification and returns failure`() = runTest {
        val json = messageJson(imageDto())
        givenInput(json)
        val uri = mockk<Uri>()
        every { Uri.parse("content://media/1") } returns uri
        every { contentResolver.openInputStream(uri) } returns null

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        verify { notifier.showFailed("m1", json) }
        coVerify(exactly = 0) { chatRepository.sendMessage(any()) }
    }

    @Test
    fun `send failure with local media shows failed notification`() = runTest {
        val json = messageJson(imageDto())
        givenInput(json)
        givenLocalMediaReadable()
        coEvery { chatRepository.uploadImage(any(), any()) } returns "https://cdn/m1.jpg"
        coEvery { chatRepository.sendMessage(any()) } returns Result.failure(IOException())

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        verify { notifier.showFailed("m1", json) }
    }
}
