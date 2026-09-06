package com.example.chatapp.features.chat.data.paging

import androidx.paging.PagingSource
import com.example.chatapp.features.chat.data.datasource.ChatDatasource
import com.example.chatapp.features.chat.data.dto.MessageDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesPagingSourceTest {

    private val dataSource = mockk<ChatDatasource>()
    private val pagingSource = MessagesPagingSource(dataSource)

    private fun dto(id: String, createdAt: String) = MessageDto(
        id = id,
        deviceId = "d1",
        mediaType = "text",
        content = "msg $id",
        createdAt = createdAt,
        username = "alice"
    )

    @Test
    fun `initial load returns page with nextKey from last item`() = runTest {
        val dtos = listOf(
            dto("m2", "2026-01-01T10:01:00Z"),
            dto("m1", "2026-01-01T10:00:00Z")
        )
        coEvery { dataSource.getMessagesPage(before = null, pageSize = 10) } returns dtos

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page
        assertEquals(2, page.data.size)
        assertEquals("m2", page.data[0].id)
        assertNull(page.prevKey)
        assertEquals("2026-01-01T10:00:00Z", page.nextKey)
    }

    @Test
    fun `append passes cursor as before parameter`() = runTest {
        val cursor = "2026-01-01T10:00:00Z"
        coEvery {
            dataSource.getMessagesPage(before = Instant.parse(cursor), pageSize = 10)
        } returns listOf(dto("m0", "2026-01-01T09:59:00Z"))

        val result = pagingSource.load(
            PagingSource.LoadParams.Append(key = cursor, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        coVerify { dataSource.getMessagesPage(before = Instant.parse(cursor), pageSize = 10) }
    }

    @Test
    fun `empty page returns null nextKey`() = runTest {
        coEvery { dataSource.getMessagesPage(before = null, pageSize = 10) } returns emptyList()

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        assertNull((result as PagingSource.LoadResult.Page).nextKey)
    }

    @Test
    fun `datasource error returns LoadResult Error`() = runTest {
        coEvery { dataSource.getMessagesPage(any(), any()) } throws IOException("boom")

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Error)
        assertTrue((result as PagingSource.LoadResult.Error).throwable is IOException)
    }
}
