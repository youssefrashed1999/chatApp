package com.example.chatapp.features.users.presentation.createProfile.viewModel
import android.net.Uri

sealed class CreateProfileIntent {
    data class UsernameChanged(val username: String) : CreateProfileIntent()
    data class ImageSelected(val uri: Uri?) : CreateProfileIntent()
    data object Submit : CreateProfileIntent()
    data object ClearError : CreateProfileIntent()
    data object ConsumedSuccess : CreateProfileIntent()
}