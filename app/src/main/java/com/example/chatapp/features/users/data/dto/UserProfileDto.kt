package com.example.chatapp.features.users.data.dto

import com.example.chatapp.features.users.domain.entity.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    @SerialName("device_id") val deviceId: String,
    val username: String,
    @SerialName("profile_image_url") val profileImageUrl: String? = null
)
fun UserProfileDto.toEntity() = UserProfile(
    deviceId = deviceId,
    username = username,
    profileImageUrl = profileImageUrl
)