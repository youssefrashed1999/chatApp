package com.example.chatapp.features.chat.presentation.viewModel

import android.net.Uri
import com.example.chatapp.features.chat.domain.entity.Message

sealed class ChatIntent {
    data class InputChanged(val text: String) : ChatIntent()
    data object Send : ChatIntent()
    data class ImagesPicked(val uris: List<Uri>) : ChatIntent()
    data class RemovePendingImage(val uri: Uri) : ChatIntent()
    data class Retry(val message: Message) : ChatIntent()
    data class Cancel(val message: Message) : ChatIntent()
    data object ClearError : ChatIntent()

    data object RefreshMessages : ChatIntent()
}
