package com.example.chatapp.features.users.domain.repository

import com.example.chatapp.features.users.domain.entity.UserProfile

interface UserRepository {
    suspend fun getCurrentUser(forceRefresh: Boolean = false): UserProfile?
    suspend fun registerUser(username: String, imageBytes: ByteArray?): Result<UserProfile>
}