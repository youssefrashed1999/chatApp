package com.example.chatapp.features.users.data.repository

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
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

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getCurrentUser(forceRefresh: Boolean): UserProfile? = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedUser()?.let { return@withContext it }
        try {
            dataSource.getUser(deviceId)?.toEntity()?.also { cacheUser(it) } ?: run {
                clearCachedUser()
                null
            }
        } catch (e: Throwable) {
            throw e.toDomainException()
        }
    }

    override suspend fun registerUser(
        username: String,
        imageBytes: ByteArray?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching { dataSource.registerUser(deviceId, username, imageBytes).toEntity() }
            .onSuccess { cacheUser(it) }
            .mapException()
    }

    private fun cachedUser(): UserProfile? {
        val id = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it == deviceId } ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        return UserProfile(
            deviceId = id,
            username = username,
            profileImageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, null)
        )
    }

    private fun cacheUser(user: UserProfile) {
        prefs.edit {
            putString(KEY_DEVICE_ID, user.deviceId)
            putString(KEY_USERNAME, user.username)
            putString(KEY_PROFILE_IMAGE_URL, user.profileImageUrl)
        }
    }

    private fun clearCachedUser() {
        prefs.edit { clear() }
    }

    private fun <T> Result<T>.mapException(): Result<T> {
        val exception = exceptionOrNull() ?: return this
        return Result.failure(exception.toDomainException())
    }

    private companion object {
        const val PREFS_NAME = "user_session"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_USERNAME = "username"
        const val KEY_PROFILE_IMAGE_URL = "profile_image_url"
    }
}