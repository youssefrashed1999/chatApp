package com.example.chatapp.features.chat.domain.entity

data class MessageContent(
    val type: MediaType,
    val text: String? = null,
    val mediaUrls: List<String> = emptyList()
) {
    companion object {
        fun text(body: String) = MessageContent(type = MediaType.TEXT, text = body)
        fun images(urls: List<String>, caption: String? = null) = MessageContent(type = MediaType.IMAGE, mediaUrls = urls, text = caption)
        fun audio(url: String, caption: String? = null) = MessageContent(type = MediaType.AUDIO, mediaUrls = listOf(url), text = caption)
    }
}