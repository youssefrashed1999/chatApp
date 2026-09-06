package com.example.chatapp.features.users.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.chatapp.core.error.NetworkTimeoutException
import com.example.chatapp.core.error.NoInternetException
import com.example.chatapp.features.users.data.datasource.UserDataSource
import com.example.chatapp.features.users.data.dto.UserProfileDto
import com.example.chatapp.features.users.data.repository.UserRepositoryImpl.Companion.KEY_DEVICE_ID
import com.example.chatapp.features.users.data.repository.UserRepositoryImpl.Companion.KEY_PROFILE_IMAGE_URL
import com.example.chatapp.features.users.data.repository.UserRepositoryImpl.Companion.KEY_USERNAME
import com.example.chatapp.features.users.data.repository.UserRepositoryImpl.Companion.PREFS_NAME
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class UserRepositoryImplTest {

    private companion object {
        const val DEVICE_ID = "device-123"
    }

    private val dataSource = mockk<UserDataSource>()
    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()

    private val repository by lazy { UserRepositoryImpl(dataSource, context) }

    @Before
    fun setUp() {
        mockkStatic(Settings.Secure::class)
        every { context.contentResolver } returns contentResolver
        every { Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) } returns DEVICE_ID
        every { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.clear() } returns editor
        every { editor.apply() } just Runs
    }

    @After
    fun tearDown() = unmockkAll()

    private fun givenCachedUser() {
        every { prefs.getString(KEY_DEVICE_ID, null) } returns DEVICE_ID
        every { prefs.getString(KEY_USERNAME, null) } returns "alice"
        every { prefs.getString(KEY_PROFILE_IMAGE_URL, null) } returns "https://cdn/avatar.jpg"
    }

    private fun givenNoCachedUser() {
        every { prefs.getString(KEY_DEVICE_ID, null) } returns null
    }

    @Test
    fun `cached user is returned without hitting the network`() = runTest {
        givenCachedUser()

        val user = repository.getCurrentUser()

        assertEquals(DEVICE_ID, user?.deviceId)
        assertEquals("alice", user?.username)
        assertEquals("https://cdn/avatar.jpg", user?.profileImageUrl)
        coVerify(exactly = 0) { dataSource.getUser(any()) }
    }

    @Test
    fun `no cache fetches remote and caches the result`() = runTest {
        givenNoCachedUser()
        coEvery { dataSource.getUser(DEVICE_ID) } returns
            UserProfileDto(DEVICE_ID, "alice", "https://cdn/avatar.jpg")

        val user = repository.getCurrentUser()

        assertEquals("alice", user?.username)
        coVerify { dataSource.getUser(DEVICE_ID) }
        verify { editor.putString(KEY_DEVICE_ID, DEVICE_ID) }
        verify { editor.putString(KEY_USERNAME, "alice") }
        verify { editor.apply() }
    }

    @Test
    fun `forceRefresh bypasses the cache and queries the backend`() = runTest {
        givenCachedUser()
        coEvery { dataSource.getUser(DEVICE_ID) } returns
            UserProfileDto(DEVICE_ID, "alice-new", null)

        val user = repository.getCurrentUser(forceRefresh = true)

        assertEquals("alice-new", user?.username)
        coVerify { dataSource.getUser(DEVICE_ID) }
    }

    @Test
    fun `forceRefresh with deleted remote user clears cache and returns null`() = runTest {
        givenCachedUser()
        coEvery { dataSource.getUser(DEVICE_ID) } returns null

        val user = repository.getCurrentUser(forceRefresh = true)

        assertNull(user)
        verify { editor.clear() }
        verify { editor.apply() }
    }

    @Test
    fun `datasource error is mapped to a domain exception`() = runTest {
        givenNoCachedUser()
        coEvery { dataSource.getUser(DEVICE_ID) } throws UnknownHostException()

        try {
            repository.getCurrentUser()
            fail("Expected NoInternetException")
        } catch (_: NoInternetException) {
            // expected
        }
    }

    @Test
    fun `registerUser success caches and returns the user`() = runTest {
        coEvery { dataSource.registerUser(DEVICE_ID, "alice", null) } returns
            UserProfileDto(DEVICE_ID, "alice", null)

        val result = repository.registerUser("alice", null)

        assertTrue(result.isSuccess)
        assertEquals("alice", result.getOrNull()?.username)
        verify { editor.putString(KEY_USERNAME, "alice") }
    }

    @Test
    fun `registerUser failure returns mapped failure`() = runTest {
        coEvery { dataSource.registerUser(any(), any(), any()) } throws SocketTimeoutException()

        val result = repository.registerUser("alice", null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkTimeoutException)
    }
}
