package com.example.chatapp.features.users.domain.usecase

import com.example.chatapp.features.users.domain.entity.UserProfile
import com.example.chatapp.features.users.domain.repository.UserRepository
import javax.inject.Inject

class RegisterUserUseCase @Inject constructor(
    private val repository: UserRepository
) {
    suspend operator fun invoke(username: String, imageBytes: ByteArray?): Result<UserProfile> {
        return repository.registerUser(username.trim(), imageBytes)
    }
}