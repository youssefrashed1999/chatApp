package com.example.chatapp.features.chat.presentation.viewModel

data class ChatState(
    val inputText: String = "",
    val currentDeviceId: String? = null,
    val errorMessage: String? = null,
)
