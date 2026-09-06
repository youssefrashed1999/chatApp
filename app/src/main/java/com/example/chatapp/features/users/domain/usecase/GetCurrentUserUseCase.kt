package com.example.chatapp.features.users.domain.usecase

import com.example.chatapp.features.users.domain.entity.UserProfile
import com.example.chatapp.features.users.domain.repository.UserRepository
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val repository: UserRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): UserProfile? =
        repository.getCurrentUser(forceRefresh)
}