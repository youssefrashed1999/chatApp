package com.example.chatapp.features.users.presentation.onboarding.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.core.error.toUserMessage
import com.example.chatapp.features.users.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OnboardingState())
    val uiState: StateFlow<OnboardingState> = _uiState.asStateFlow()

    init {
        handleIntent(OnboardingIntent.CheckUser)
    }

    fun handleIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.CheckUser, OnboardingIntent.Retry -> checkUser()
            OnboardingIntent.ConsumedChatNavigation -> _uiState.update { it.copy(navigateToChat = false) }
            OnboardingIntent.ConsumedCreateProfileNavigation -> _uiState.update { it.copy(navigateToCreateProfile = false) }
        }
    }

    private fun checkUser() {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                navigateToChat = false,
                navigateToCreateProfile = false
            )
        }

        viewModelScope.launch {
            try {
                val user = getCurrentUserUseCase()
                if (user != null) {
                    _uiState.update { it.copy(isLoading = false, navigateToChat = true) }
                } else {
                    _uiState.update { it.copy(isLoading = false, navigateToCreateProfile = true) }
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toUserMessage(application)
                    )
                }
            }
        }
    }
}