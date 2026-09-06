package com.example.chatapp.features.chat.presentation.viewModel

import android.net.Uri

data class ChatState(
    val inputText: String = "",
    val pendingImageUris: List<Uri> = emptyList(),
    val isRecording: Boolean = false,
    val currentDeviceId: String? = null,
    val errorMessage: String? = null,
)
