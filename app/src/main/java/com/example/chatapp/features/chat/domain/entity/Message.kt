package com.example.chatapp.features.chat.domain.entity

import com.example.chatapp.features.users.domain.entity.UserProfile
import kotlinx.datetime.Instant

data class Message(
    val id: String,
    val sender: UserProfile,
    val content: MessageContent,
    val status: SendStatus,
    val createdAt: Instant
)
