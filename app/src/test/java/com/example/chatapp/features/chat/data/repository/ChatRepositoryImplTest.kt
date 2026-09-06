package com.example.chatapp.features.chat.data.repository

import com.example.chatapp.features.chat.data.datasource.ChatDatasource
import com.example.chatapp.features.chat.data.dto.MessageDto
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.entity.MessageContent
import com.example.chatapp.features.chat.domain.entity.SendStatus
import com.example.chatapp.features.users.domain.entity.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryImplTest {

    private val dataSource = mockk<ChatDatasource>()
    private val repository = ChatRepositoryImpl(dataSource)

    private val testInstant = Instant.parse("2026-01-01T10:00:00Z")

    private fun textMessage(id: String = "m1") = Message(
        id = id,
        sender = UserProfile(deviceId = "d1", username = "alice", profileImageUrl = null),
        content = MessageContent.text("hello"),
        status = SendStatus.SENDING,
        createdAt = testInstant
    )

    private fun textDto(id: String = "m1") = MessageDto(
        id = id,
        deviceId = "d1",
        mediaType = "text",
        content = "hello",
        createdAt = testInstant.toString(),
        username = "alice"
    )

    @Test
    fun `sendMessage success returns success and forwards mapped dto`() = runTest {
        val message = textMessage()
        coEvery { dataSource.sendMessage(any()) } returns Unit

        val result = repository.sendMessage(message)

        assertTrue(result.isSuccess)
        coVerify {
            dataSource.sendMessage(match { dto ->
                dto.id == message.id &&
                    dto.deviceId == message.sender.deviceId &&
                    dto.mediaType == "text" &&
                    dto.content == "hello"
            })
        }
    }

    @Test
    fun `sendMessage failure returns failure`() = runTest {
        coEvery { dataSource.sendMessage(any()) } throws IOException("no connection")

        val result = repository.sendMessage(textMessage())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `refreshMessages maps dtos to entities`() = runTest {
        coEvery { dataSource.refreshMessages(testInstant) } returns listOf(textDto("m1"), textDto("m2"))

        val messages = repository.refreshMessages(testInstant)

        assertEquals(2, messages.size)
        assertEquals("m1", messages[0].id)
        assertEquals("alice", messages[0].sender.username)
        assertEquals(SendStatus.SENT, messages[0].status)
        coVerify { dataSource.refreshMessages(testInstant) }
    }

    @Test
    fun `observeLiveMessages maps incoming dtos to entities`() = runTest {
        every { dataSource.observeMessageInserts() } returns flowOf(textDto("live-1"))

        val message = repository.observeLiveMessages().first()

        assertEquals("live-1", message.id)
        assertEquals("hello", message.content.text)
        assertEquals(SendStatus.SENT, message.status)
    }

    @Test
    fun `uploadImage delegates to datasource and returns url`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        coEvery { dataSource.uploadImage("messages/m1/0.jpg", bytes) } returns "https://cdn/m1.jpg"

        val url = repository.uploadImage("messages/m1/0.jpg", bytes)

        assertEquals("https://cdn/m1.jpg", url)
        coVerify { dataSource.uploadImage("messages/m1/0.jpg", bytes) }
    }
}
