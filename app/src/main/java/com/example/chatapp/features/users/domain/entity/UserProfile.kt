package com.example.chatapp.features.users.domain.entity

data class UserProfile(
    val deviceId: String,
    val username: String,
    val profileImageUrl: String?,
)