package com.example.chatapp.core

object Constants {
    object Database {
        const val USERS_TABLE = "users"
        const val MESSAGES_TABLE = "messages"
        const val DEVICE_ID_COLUMN = "device_id"
        const val CREATED_AT_COLUMN = "created_at"
        const val MESSAGES_INSERTS_CHANNEL = "messages-inserts"
        const val PUBLIC_SCHEMA = "public"
    }

    object Storage {
        const val BUCKET = "chat-media"
        const val PROFILE_IMAGE_PREFIX = "profiles"
        const val PROFILE_IMAGE_EXTENSION = "jpg"
        const val MESSAGE_MEDIA_PREFIX = "messages"
    }

    object Navigation {
        const val ONBOARDING_ROUTE = "onboarding"
        const val CREATE_PROFILE_ROUTE = "create_profile"
        const val CHAT_ROUTE = "chat"
    }

    object DateTime {
        const val TIME_FORMAT = "%02d:%02d"
        const val DATE_TIME_FORMAT = "%02d/%02d %s"
        const val DATE_TIME_YEAR_FORMAT = "%02d/%02d/%04d %s"
    }

    object Notifications {
        const val MESSAGE_SEND_CHANNEL_ID = "message_send"
    }

    const val PAGE_SIZE = 10
    const val MAX_IMAGES_PER_MESSAGE = 10

    fun getProfileImageStoragePath(deviceId: String) = "${Storage.PROFILE_IMAGE_PREFIX}/$deviceId.${Storage.PROFILE_IMAGE_EXTENSION}"

    fun getMessageMediaStoragePath(messageId: String, index: Int, extension: String) =
        "${Storage.MESSAGE_MEDIA_PREFIX}/$messageId/$index.$extension"
}