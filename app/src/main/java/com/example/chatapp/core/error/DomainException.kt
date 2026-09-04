package com.example.chatapp.core.error

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class DomainException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class NoInternetException(cause: Throwable? = null) :
    DomainException("No internet connection", cause)

class NetworkException(cause: Throwable? = null) :
    DomainException("A network error occurred", cause)

class NetworkTimeoutException(cause: Throwable? = null) :
    DomainException("Connection timed out", cause)

class ClientErrorException(
    message: String? = null,
    cause: Throwable? = null
) : DomainException(message ?: "Request failed", cause)

class ServerErrorException(
    message: String? = null,
    cause: Throwable? = null
) : DomainException(message ?: "Server error", cause)

class UnknownDomainException(cause: Throwable? = null) :
    DomainException("Something went wrong", cause)

fun Throwable.toDomainException(): DomainException {
    if (this is DomainException) return this

    val chain = generateSequence(this) { it.cause }.toList()

    return when {
        this is RestException -> when (statusCode) {
            in 400..499 -> ClientErrorException(description ?: error, this)
            else -> ServerErrorException(description ?: error, this)
        }

        this is ResponseException -> when (response.status.value) {
            in 500..599 -> ServerErrorException(response.status.description, this)
            in 400..499 -> ClientErrorException(response.status.description, this)
            else -> ClientErrorException(response.status.description, this)
        }

        chain.any { it is SocketTimeoutException || it is ConnectTimeoutException || it is HttpRequestTimeoutException } -> NetworkTimeoutException(this)

        chain.any { it is UnknownHostException } -> NoInternetException(this)

        chain.any { it is ConnectException } -> NetworkException(this)

        chain.any { it is SocketException } -> NetworkException(this)

        chain.any { it is IOException } -> NetworkException(this)

        else -> UnknownDomainException(this)
    }
}
