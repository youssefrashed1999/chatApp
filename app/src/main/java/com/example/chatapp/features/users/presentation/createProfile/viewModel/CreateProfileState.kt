package com.example.chatapp.features.users.presentation.createProfile.viewModel
import android.net.Uri

data class CreateProfileState(
    val username: String = "",
    val selectedImageUri: Uri? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
)