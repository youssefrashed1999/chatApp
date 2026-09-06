package com.example.chatapp.features.users.presentation.onboarding

import android.app.Application
import com.example.chatapp.core.error.NoInternetException
import com.example.chatapp.features.users.domain.entity.UserProfile
import com.example.chatapp.features.users.domain.usecase.GetCurrentUserUseCase
import com.example.chatapp.features.users.presentation.onboarding.viewModel.OnboardingIntent
import com.example.chatapp.features.users.presentation.onboarding.viewModel.OnboardingViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getCurrentUserUseCase = mockk<GetCurrentUserUseCase>()
    private val application = mockk<Application>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = OnboardingViewModel(getCurrentUserUseCase, application)

    @Test
    fun `existing remote user navigates to chat`() = runTest(testDispatcher) {
        coEvery { getCurrentUserUseCase(forceRefresh = true) } returns
            UserProfile("d1", "alice", null)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.navigateToChat)
        assertFalse(state.navigateToCreateProfile)
        assertNull(state.errorMessage)
        coVerify { getCurrentUserUseCase(forceRefresh = true) }
    }

    @Test
    fun `missing remote user navigates to create profile`() = runTest(testDispatcher) {
        coEvery { getCurrentUserUseCase(forceRefresh = true) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.navigateToCreateProfile)
        assertFalse(state.navigateToChat)
    }

    @Test
    fun `check failure exposes an error message`() = runTest(testDispatcher) {
        coEvery { getCurrentUserUseCase(forceRefresh = true) } throws NoInternetException()
        every { application.getString(any()) } returns "no internet"

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("no internet", state.errorMessage)
        assertFalse(state.navigateToChat)
        assertFalse(state.navigateToCreateProfile)
    }

    @Test
    fun `retry intent runs the remote check again`() = runTest(testDispatcher) {
        coEvery { getCurrentUserUseCase(forceRefresh = true) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.handleIntent(OnboardingIntent.Retry)
        advanceUntilIdle()

        coVerify(exactly = 2) { getCurrentUserUseCase(forceRefresh = true) }
    }

    @Test
    fun `consumed chat navigation clears the flag`() = runTest(testDispatcher) {
        coEvery { getCurrentUserUseCase(forceRefresh = true) } returns
            UserProfile("d1", "alice", null)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.handleIntent(OnboardingIntent.ConsumedChatNavigation)

        assertFalse(viewModel.uiState.value.navigateToChat)
    }
}
