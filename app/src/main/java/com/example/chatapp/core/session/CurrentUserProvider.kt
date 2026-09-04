package com.example.chatapp.core.session

import com.example.chatapp.features.users.domain.entity.UserProfile

interface CurrentUserProvider {
    suspend fun requireCurrentUser(): UserProfile
}