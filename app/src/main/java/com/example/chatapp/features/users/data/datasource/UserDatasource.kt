package com.example.chatapp.features.users.data.datasource

import com.example.chatapp.core.Constants.Database.DEVICE_ID_COLUMN
import com.example.chatapp.core.Constants.Database.USERS_TABLE
import com.example.chatapp.core.Constants.Storage.BUCKET
import com.example.chatapp.core.Constants.getProfileImageStoragePath
import com.example.chatapp.features.users.data.dto.UserProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import javax.inject.Inject

class UserDataSource @Inject constructor(private val supabase: SupabaseClient) {
    suspend fun getUser(deviceId: String): UserProfileDto? =
        supabase.from(USERS_TABLE)
            .select { filter { eq(DEVICE_ID_COLUMN, deviceId) } }
            .decodeSingleOrNull<UserProfileDto>()

    suspend fun registerUser(deviceId: String, username: String, imageBytes: ByteArray?): UserProfileDto {
        val imageUrl = imageBytes?.let { bytes ->
            val path = getProfileImageStoragePath(deviceId)
            supabase.storage.from(BUCKET).upload(path = path, data = bytes) { upsert = true }
            supabase.storage.from(BUCKET).publicUrl(path)
        }

        val dto = UserProfileDto(deviceId = deviceId, username = username, profileImageUrl = imageUrl)
        supabase.from(USERS_TABLE).upsert(dto)
        return dto
    }
}