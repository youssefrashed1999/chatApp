package com.example.chatapp.core.error

import android.content.Context
import com.example.chatapp.R

fun Throwable.toUserMessage(context: Context): String = when (this) {
    is NoInternetException -> context.getString(R.string.error_no_internet)
    is NetworkException -> context.getString(R.string.error_network)
    is NetworkTimeoutException -> context.getString(R.string.error_timeout)
    is ClientErrorException -> context.getString(R.string.error_client)
    is ServerErrorException -> context.getString(R.string.error_server)
    is UnknownDomainException -> context.getString(R.string.error_unknown)
    else -> message ?: context.getString(R.string.error_unknown)
}
