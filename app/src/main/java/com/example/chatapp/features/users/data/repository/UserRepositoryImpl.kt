package com.example.chatapp.features.users.data.repository

import android.content.Context
import android.provider.Settings
import com.example.chatapp.core.error.toDomainException
import com.example.chatapp.features.users.data.datasource.UserDataSource
import com.example.chatapp.features.users.data.dto.toEntity
import com.example.chatapp.features.users.domain.entity.UserProfile
import com.example.chatapp.features.users.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dataSource: UserDataSource,
    @ApplicationContext private val context: Context
) : UserRepository {

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    override suspend fun getCurrentUser(): UserProfile? = withContext(Dispatchers.IO) {
        try {
            dataSource.getUser(deviceId)?.toEntity()
        } catch (e: Throwable) {
            throw e.toDomainException()
        }
    }

    override suspend fun registerUser(
        username: String,
        imageBytes: ByteArray?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching { dataSource.registerUser(deviceId, username, imageBytes).toEntity() }
            .mapException()
    }

    private fun <T> Result<T>.mapException(): Result<T> {
        val exception = exceptionOrNull() ?: return this
        return Result.failure(exception.toDomainException())
    }
}