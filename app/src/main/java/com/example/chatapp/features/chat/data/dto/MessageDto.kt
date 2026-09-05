package com.example.chatapp.features.chat.data.dto

import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.entity.MessageContent
import com.example.chatapp.features.chat.domain.entity.SendStatus
import com.example.chatapp.features.users.domain.entity.UserProfile
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("media_type") val mediaType: String,
    val content: String? = null,
    @SerialName("media_urls") val mediaUrls: List<String>? = emptyList(),
    @SerialName("created_at") val createdAt: String,
    val username: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
)

fun MessageDto.toEntity() = Message(
    id = id,
    sender = UserProfile(
        deviceId = deviceId,
        username = username ?: "",
        profileImageUrl = profileImageUrl
    ),
    content = when (mediaType.uppercase()) {
        "IMAGE" -> MessageContent.images(mediaUrls!!, content)
        "AUDIO" -> MessageContent.audio(mediaUrls!!.firstOrNull() ?: "", content)
        else -> MessageContent.text(content ?: "")
    },
    status = SendStatus.SENT,
    createdAt = Instant.parse(createdAt)
)

fun Message.toDto() = MessageDto(
    id = id,
    deviceId = sender.deviceId,
    mediaType = content.type.name.lowercase(),
    content = content.text,
    mediaUrls = content.mediaUrls,
    createdAt = createdAt.toString(),
)
