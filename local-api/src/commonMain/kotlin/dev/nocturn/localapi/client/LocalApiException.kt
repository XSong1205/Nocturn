package dev.nocturn.localapi.client

/** A sanitized local-service error. Response bodies and credentials are never included. */
class LocalApiException(
    val code: String,
    val status: Int? = null,
    message: String,
) : Exception(message)

data class LocalApiHealth(
    val app: String,
    val version: String,
    val status: String,
)
