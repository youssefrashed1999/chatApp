package com.example.chatapp.features.users.presentation.createProfile.viewModel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.R
import com.example.chatapp.core.error.toUserMessage
import com.example.chatapp.features.users.domain.usecase.RegisterUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateProfileViewModel @Inject constructor(
    private val registerUserUseCase: RegisterUserUseCase,
    private val application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CreateProfileState())
    val uiState: StateFlow<CreateProfileState> = _uiState.asStateFlow()

    fun handleIntent(intent: CreateProfileIntent) {
        when (intent) {
            is CreateProfileIntent.UsernameChanged -> {
                _uiState.update { it.copy(username = intent.username, errorMessage = null) }
            }

            is CreateProfileIntent.ImageSelected -> {
                _uiState.update { it.copy(selectedImageUri = intent.uri, errorMessage = null) }
            }

            CreateProfileIntent.Submit -> register()
            CreateProfileIntent.ClearError -> _uiState.update { it.copy(errorMessage = null) }
            CreateProfileIntent.ConsumedSuccess -> _uiState.update { it.copy(isSuccess = false) }
        }
    }

    private fun register() {
        val current = _uiState.value
        val username = current.username.trim()
        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.create_profile_username_empty)) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val bytes = current.selectedImageUri?.let { readBytes(it) }
            registerUserUseCase(username, bytes)
                .fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = error.toUserMessage(application))
                        }
                    }
                )
        }
    }

    private fun readBytes(uri: Uri): ByteArray? = runCatching {
        application.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}