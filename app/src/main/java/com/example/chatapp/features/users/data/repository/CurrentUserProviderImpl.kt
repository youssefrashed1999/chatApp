package com.example.chatapp.features.users.data.repository

import com.example.chatapp.core.session.CurrentUserProvider
import com.example.chatapp.features.users.domain.entity.UserProfile
import com.example.chatapp.features.users.domain.usecase.GetCurrentUserUseCase
import javax.inject.Inject

class CurrentUserProviderImpl @Inject constructor(private val getCurrentUserUseCase: GetCurrentUserUseCase) : CurrentUserProvider {
    override suspend fun requireCurrentUser(): UserProfile = getCurrentUserUseCase() ?: throw IllegalStateException("User not registered")
}