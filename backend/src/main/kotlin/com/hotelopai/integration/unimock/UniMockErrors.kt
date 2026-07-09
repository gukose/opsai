package com.hotelopai.integration.unimock

open class UniMockClientException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class UniMockClientNotFoundException(message: String) : UniMockClientException(message)

class UniMockClientConflictException(message: String) : UniMockClientException(message)

class UniMockClientValidationException(message: String) : UniMockClientException(message)

class UniMockClientUnauthorizedException(message: String) : UniMockClientException(message)

class UniMockClientForbiddenException(message: String) : UniMockClientException(message)

class UniMockClientRateLimitedException(message: String) : UniMockClientException(message)

class UniMockClientUnavailableException(message: String, cause: Throwable? = null) :
    UniMockClientException(message, cause)

class UniMockClientTimeoutException(message: String, cause: Throwable? = null) :
    UniMockClientException(message, cause)

data class UniMockErrorResponseDto(
    val message: String? = null,
    val code: String? = null,
    val details: String? = null
)

internal object UniMockErrorMapper {
    fun map(
        statusCode: Int,
        rawBody: String?,
        fallbackMessage: String,
        cause: Throwable? = null
    ): UniMockClientException {
        val message = parseMessage(rawBody) ?: fallbackMessage

        return when (statusCode) {
            400, 422 -> UniMockClientValidationException(message)
            401 -> UniMockClientUnauthorizedException(message)
            403 -> UniMockClientForbiddenException(message)
            404 -> UniMockClientNotFoundException(message)
            409 -> UniMockClientConflictException(message)
            429 -> UniMockClientRateLimitedException(message)
            else -> UniMockClientUnavailableException(message, cause)
        }
    }

    fun mapTransportFailure(
        message: String,
        cause: Throwable
    ): UniMockClientException =
        when (cause) {
            is java.net.http.HttpTimeoutException -> UniMockClientTimeoutException(message, cause)
            is java.io.IOException -> UniMockClientUnavailableException(message, cause)
            else -> UniMockClientUnavailableException(message, cause)
        }

    private fun parseMessage(rawBody: String?): String? {
        if (rawBody.isNullOrBlank()) {
            return null
        }

        return runCatching {
            val node = jacksonObjectMapper().readTree(rawBody)
            node.path("message").takeIf { !it.isMissingNode && !it.isNull }?.asText()
                ?: node.path("error").takeIf { !it.isMissingNode && !it.isNull }?.asText()
                ?: rawBody
        }.getOrNull() ?: rawBody
    }

    private fun jacksonObjectMapper() = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
}
