package com.hotelopai.integration.apaleo

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.OAuth2ClientCredentialsPmsAuthenticationConfig
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsCredentialResolver
import com.hotelopai.pms.domain.PmsProviderAuthenticationException
import com.hotelopai.pms.domain.PmsProviderCircuitOpenException
import com.hotelopai.pms.domain.PmsProviderConfigurationFailureException
import com.hotelopai.pms.domain.PmsProviderInvalidRequestException
import com.hotelopai.pms.domain.PmsProviderMalformedResponseException
import com.hotelopai.pms.domain.PmsProviderPermissionException
import com.hotelopai.pms.domain.PmsProviderRateLimitException
import com.hotelopai.pms.domain.PmsProviderResourceNotFoundException
import com.hotelopai.pms.domain.PmsProviderTimeoutException
import com.hotelopai.pms.domain.PmsProviderUnavailableException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.Base64

class ApaleoPmsHttpClient(
    private val config: PmsConfiguredProviderProperties,
    private val objectMapper: ObjectMapper,
    private val credentialResolver: PmsCredentialResolver,
    private val observability: OperationalObservability,
    private val circuitBreaker: ApaleoCircuitBreaker = ApaleoCircuitBreaker("apaleo", observability),
    private val tokenCache: ApaleoOAuthTokenCache = ApaleoOAuthTokenCache(),
    private val clock: Clock = Clock.systemUTC(),
    private val sleeper: ApaleoSleeper = ThreadApaleoSleeper()
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(config.requestTimeout)
        .build()
    private val resilience = ApaleoResilienceSettings.from(config.settings)
    private val pageSize: Int = config.settings["page-size"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
    private val maxPages: Int = config.settings["max-pages"]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_MAX_PAGES

    fun getProperty(propertyId: String): ApaleoPropertyDto? =
        getNullable(
            operation = "get_property",
            path = "/inventory/v1/properties/${encodePath(propertyId)}",
            typeRef = object : TypeReference<ApaleoPropertyDto>() {}
        )

    fun listUnits(): List<ApaleoUnitDto> =
        listPages(
            operation = "list_units",
            path = "/inventory/v1/units",
            propertyQueryName = "propertyIds",
            extract = { it.units },
            typeRef = object : TypeReference<ApaleoUnitListDto>() {}
        )

    fun listBookings(): List<ApaleoBookingDto> =
        listPages(
            operation = "list_bookings",
            path = "/booking/v1/bookings",
            propertyQueryName = "propertyIds",
            extract = { it.bookings },
            typeRef = object : TypeReference<ApaleoBookingListDto>() {}
        )

    fun healthCheck(): Boolean {
        val healthConfig = config.copy(requestTimeout = minDuration(config.requestTimeout, resilience.healthCheckTimeout))
        val healthClient = ApaleoPmsHttpClient(
            config = healthConfig,
            objectMapper = objectMapper,
            credentialResolver = credentialResolver,
            observability = observability,
            circuitBreaker = circuitBreaker,
            tokenCache = tokenCache,
            clock = clock,
            sleeper = sleeper
        )
        val propertyId = config.hotelPropertyIdentifier?.takeIf { it.isNotBlank() }
        return if (propertyId != null) {
            healthClient.getProperty(propertyId) != null
        } else {
            healthClient.listUnits()
            true
        }
    }

    private fun <T, R> listPages(
        operation: String,
        path: String,
        propertyQueryName: String,
        extract: (T) -> List<R>,
        typeRef: TypeReference<T>
    ): List<R> {
        val items = mutableListOf<R>()
        for (pageNumber in 1..maxPages) {
            val query = buildMap {
                put("pageNumber", pageNumber.toString())
                put("pageSize", pageSize.toString())
                config.hotelPropertyIdentifier
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put(propertyQueryName, it) }
            }
            val page = getRequired(operation, path, query, typeRef)
            val pageItems = extract(page)
            items += pageItems
            if (pageItems.size < pageSize) {
                break
            }
        }
        return items
    }

    private fun <T> getRequired(
        operation: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        typeRef: TypeReference<T>
    ): T {
        val response = execute(operation, path, query, allowNotFound = false)
        return deserialize(operation, response.body(), typeRef)
    }

    private fun <T> getNullable(
        operation: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        typeRef: TypeReference<T>
    ): T? {
        val response = execute(operation, path, query, allowNotFound = true)
        if (response.statusCode() == 404 || response.statusCode() == 204) {
            return null
        }
        return deserialize(operation, response.body(), typeRef)
    }

    private fun execute(
        operation: String,
        path: String,
        query: Map<String, String>,
        allowNotFound: Boolean
    ): HttpResponse<String> {
        try {
            circuitBreaker.beforeCall(operation, resilience.circuitBreaker)
        } catch (exception: ApaleoCircuitOpenException) {
            circuitBreaker.onPermanentFailure("circuit_open")
            throw PmsProviderCircuitOpenException(exception.message ?: "Apaleo circuit is open.", exception)
        }
        var token = requestAccessToken()
        var tokenInvalidatedAfterAuthenticationFailure = false
        var attempt = 1
        var lastFailure: RuntimeException? = null

        while (attempt <= resilience.retry.maxAttempts) {
            val sample = observability.startTimer()
            var outcome = "failure"
            var statusTag = "unknown"
            try {
                val response = httpClient.send(
                    buildApiRequest(path, query, token),
                    HttpResponse.BodyHandlers.ofString()
                )
                statusTag = response.statusCode().toString()
                if (response.statusCode() in 200..299) {
                    outcome = "success"
                    circuitBreaker.onSuccess()
                    return response
                }
                if (allowNotFound && response.statusCode() == 404) {
                    outcome = "not_found"
                    circuitBreaker.onPermanentFailure("not_found")
                    return response
                }

                if (response.statusCode() == 401 && !tokenInvalidatedAfterAuthenticationFailure) {
                    tokenInvalidatedAfterAuthenticationFailure = true
                    tokenCache.invalidate()
                    token = requestAccessToken()
                    outcome = "reauthenticate"
                    observability.incrementCounter(
                        "pms_provider_token_refresh_total",
                        "provider" to "apaleo",
                        "operation" to operation,
                        "outcome" to "reauthenticate",
                        "status" to "401"
                    )
                    continue
                }

                val failure = mapFailure(operation, response)
                val retryDelay = retryDelayFor(failure, response, attempt)
                if (retryDelay == null) {
                    recordFailure(failure)
                    throw failure
                }
                outcome = "retry"
                lastFailure = failure
                recordRetry(operation, statusTag, attempt)
                sleeper.sleep(retryDelay)
            } catch (exception: java.net.http.HttpTimeoutException) {
                statusTag = "timeout"
                val failure = PmsProviderTimeoutException("Apaleo request timed out for operation '$operation'.", exception)
                val retryDelay = retryDelayFor(failure, null, attempt)
                if (retryDelay == null) {
                    outcome = "timeout"
                    recordFailure(failure)
                    throw failure
                }
                outcome = "retry"
                lastFailure = failure
                recordRetry(operation, statusTag, attempt)
                sleeper.sleep(retryDelay)
            } catch (exception: IOException) {
                statusTag = "transport"
                val failure = PmsProviderUnavailableException(
                    "Apaleo request failed during transport for operation '$operation'.",
                    exception
                )
                val retryDelay = retryDelayFor(failure, null, attempt)
                if (retryDelay == null) {
                    recordFailure(failure)
                    throw failure
                }
                outcome = "retry"
                lastFailure = failure
                recordRetry(operation, statusTag, attempt)
                sleeper.sleep(retryDelay)
            } finally {
                observability.incrementCounter(
                    "pms_provider_requests_total",
                    "provider" to "apaleo",
                    "operation" to operation,
                    "outcome" to outcome,
                    "status" to statusTag
                )
                observability.stopTimer(
                    sample,
                    "pms_provider_request_duration",
                    "provider" to "apaleo",
                    "operation" to operation,
                    "outcome" to outcome,
                    "status" to statusTag
                )
            }
            attempt += 1
        }

        val failure = lastFailure ?: PmsProviderUnavailableException("Apaleo request failed for operation '$operation'.")
        recordFailure(failure)
        throw failure
    }

    private fun buildApiRequest(path: String, query: Map<String, String>, token: String): HttpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(resolveUrl(path, query)))
            .timeout(config.requestTimeout)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .GET()
            .build()

    private fun retryDelayFor(
        failure: RuntimeException,
        response: HttpResponse<String>?,
        attempt: Int
    ): Duration? {
        if (attempt >= resilience.retry.maxAttempts || !failure.isRetryable()) {
            return null
        }

        if (failure is PmsProviderRateLimitException) {
            val retryAfter = failure.retryAfterSeconds?.let { Duration.ofSeconds(it) }
            if (retryAfter == null || retryAfter > resilience.retry.maxRateLimitDelay) {
                observability.incrementCounter(
                    "pms_provider_rate_limit_total",
                    "provider" to "apaleo",
                    "operation" to "rate_limit",
                    "outcome" to "fail_fast",
                    "status" to (response?.statusCode()?.toString() ?: "429")
                )
                return null
            }
            observability.incrementCounter(
                "pms_provider_rate_limit_total",
                "provider" to "apaleo",
                "operation" to "rate_limit",
                "outcome" to "retry",
                "status" to "429"
            )
            return retryAfter
        }

        return resilience.retry.delayForAttempt(attempt)
    }

    private fun recordRetry(operation: String, statusTag: String, attempt: Int) {
        observability.incrementCounter(
            "pms_provider_retries_total",
            "provider" to "apaleo",
            "operation" to operation,
            "outcome" to "retry",
            "status" to statusTag
        )
        observability.setGauge(
            "pms_provider_last_attempt_count",
            attempt.toLong() + 1,
            "provider" to "apaleo",
            "operation" to operation,
            "outcome" to "retry",
            "status" to statusTag
        )
    }

    private fun recordFailure(failure: RuntimeException) {
        if (failure.isTransientAvailabilityFailure()) {
            circuitBreaker.onTransientFailure(failure.safeCategory(), resilience.circuitBreaker)
        } else {
            circuitBreaker.onPermanentFailure(failure.safeCategory())
        }
    }

    private fun requestAccessToken(): String {
        val auth = config.authenticationConfig()
        if (auth !is OAuth2ClientCredentialsPmsAuthenticationConfig) {
            throw PmsProviderConfigurationFailureException("Apaleo requires OAuth2 client credentials authentication.")
        }
        return tokenCache.getOrAcquire(resilience.tokenExpirySafetyWindow) {
            requestAccessTokenFromProvider(auth)
        }
    }

    private fun requestAccessTokenFromProvider(auth: OAuth2ClientCredentialsPmsAuthenticationConfig): ApaleoCachedToken {
        val clientId = credentialResolver.resolve(auth.clientIdReference)
        val clientSecret = credentialResolver.resolve(auth.clientSecretReference)
        val credentials = Base64.getEncoder()
            .encodeToString("$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8))
        val form = buildList {
            add("grant_type=client_credentials")
            if (auth.scopes.isNotEmpty()) {
                add("scope=${encodeQuery(auth.scopes.joinToString(" "))}")
            }
        }.joinToString("&")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(auth.tokenUrl))
            .timeout(config.requestTimeout)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Basic $credentials")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: java.net.http.HttpTimeoutException) {
            throw PmsProviderTimeoutException("Apaleo token request timed out.", exception)
        } catch (exception: IOException) {
            throw PmsProviderUnavailableException("Apaleo token request failed during transport.", exception)
        }

        if (response.statusCode() !in 200..299) {
            throw mapFailure("request_token", response)
        }

        val token = deserialize("request_token", response.body(), object : TypeReference<ApaleoTokenResponseDto>() {})
        if (!token.tokenType.equals("Bearer", ignoreCase = true) || token.accessToken.isNullOrBlank()) {
            throw PmsProviderMalformedResponseException("Apaleo token response did not contain a usable bearer token.")
        }
        return ApaleoCachedToken(
            value = token.accessToken,
            expiresAt = clock.instant().plusSeconds(token.expiresIn?.coerceAtLeast(1) ?: DEFAULT_TOKEN_EXPIRES_IN_SECONDS)
        )
    }

    private fun <T> deserialize(operation: String, body: String, typeRef: TypeReference<T>): T =
        try {
            objectMapper.readValue(body, typeRef)
        } catch (exception: Exception) {
            throw PmsProviderMalformedResponseException(
                "Apaleo response for operation '$operation' could not be parsed.",
                exception
            )
        }

    private fun mapFailure(operation: String, response: HttpResponse<String>): RuntimeException {
        val trackingId = response.headers().firstValue("Apaleo-Tracking-Id").orElse(null)
        val message = buildString {
            append("Apaleo request failed for operation '$operation' with status ${response.statusCode()}")
            if (!trackingId.isNullOrBlank()) {
                append(" trackingId=$trackingId")
            }
            append(".")
        }

        return when (response.statusCode()) {
            400, 422 -> PmsProviderInvalidRequestException(message)
            401 -> PmsProviderAuthenticationException(message)
            403 -> PmsProviderPermissionException(message)
            404 -> PmsProviderResourceNotFoundException(message)
            429 -> PmsProviderRateLimitException(
                message = message,
                retryAfterSeconds = response.headers().firstValue("Retry-After").orElse(null)?.toLongOrNull()
            )
            in 500..599 -> PmsProviderUnavailableException(message)
            else -> PmsProviderUnavailableException(message)
        }
    }

    private fun resolveUrl(path: String, query: Map<String, String>): String {
        val base = config.endpoint?.trimEnd('/')
            ?: throw PmsProviderConfigurationFailureException("Apaleo endpoint is not configured.")
        val queryString = query.entries
            .sortedBy { it.key }
            .joinToString("&") { "${encodeQuery(it.key)}=${encodeQuery(it.value)}" }
        return if (queryString.isBlank()) {
            "$base$path"
        } else {
            "$base$path?$queryString"
        }
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun RuntimeException.isRetryable(): Boolean =
        this is PmsProviderTimeoutException ||
            this is PmsProviderUnavailableException ||
            this is PmsProviderRateLimitException

    private fun RuntimeException.isTransientAvailabilityFailure(): Boolean =
        this is PmsProviderTimeoutException ||
            this is PmsProviderUnavailableException ||
            this is PmsProviderRateLimitException

    private fun RuntimeException.safeCategory(): String =
        when (this) {
            is PmsProviderAuthenticationException -> "authentication"
            is PmsProviderPermissionException -> "permission"
            is PmsProviderInvalidRequestException -> "validation"
            is PmsProviderResourceNotFoundException -> "not_found"
            is PmsProviderRateLimitException -> "rate_limit"
            is PmsProviderTimeoutException -> "timeout"
            is PmsProviderMalformedResponseException -> "malformed_response"
            is PmsProviderConfigurationFailureException -> "configuration"
            is PmsProviderCircuitOpenException -> "circuit_open"
            is PmsProviderUnavailableException -> "provider_unavailable"
            else -> "unknown"
        }

    private fun minDuration(first: Duration, second: Duration): Duration =
        if (first <= second) first else second

    companion object {
        private const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = 200
        private const val DEFAULT_MAX_PAGES = 2
        private const val DEFAULT_TOKEN_EXPIRES_IN_SECONDS = 3600L
    }
}
