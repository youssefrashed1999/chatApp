package com.example.chatapp.core

object Constants {
    object Database {
        const val USERS_TABLE = "users"
        const val DEVICE_ID_COLUMN = "device_id"
    }

    object Storage {
        const val BUCKET = "chat-media"
        const val PROFILE_IMAGE_PREFIX = "profiles"
        const val PROFILE_IMAGE_EXTENSION = "jpg"
    }

    object Navigation {
        const val ONBOARDING_ROUTE = "onboarding"
        const val CREATE_PROFILE_ROUTE = "create_profile"
        const val CHAT_ROUTE = "chat"
    }

    fun getProfileImageStoragePath(deviceId: String) = "${Storage.PROFILE_IMAGE_PREFIX}/$deviceId.${Storage.PROFILE_IMAGE_EXTENSION}"
}