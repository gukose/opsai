package com.hotelopai.reservation.recommendation

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun interface RecommendationCredentialResolver {
    fun resolve(reference: RecommendationCredentialReference): String
}

@Component
class EnvironmentRecommendationCredentialResolver : RecommendationCredentialResolver {
    override fun resolve(reference: RecommendationCredentialReference): String =
        when (reference.source) {
            RecommendationCredentialSource.ENVIRONMENT ->
                System.getenv(reference.name)?.takeIf { it.isNotBlank() }
                    ?: throw RecommendationProviderException(RecommendationFailureCategory.INVALID_CONFIGURATION)
            RecommendationCredentialSource.SECRET_REFERENCE,
            RecommendationCredentialSource.VAULT ->
                throw RecommendationProviderException(RecommendationFailureCategory.INVALID_CONFIGURATION)
        }
}

data class RecommendationHttpRequest(
    val endpoint: URI,
    val timeout: Duration,
    val headers: Map<String, String>,
    val body: String
)

data class RecommendationHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String
)

interface RecommendationHttpClient {
    fun postJson(request: RecommendationHttpRequest): RecommendationHttpResponse
}

class JdkRecommendationHttpClient : RecommendationHttpClient {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun postJson(request: RecommendationHttpRequest): RecommendationHttpResponse {
        val builder = HttpRequest.newBuilder()
            .uri(request.endpoint)
            .timeout(request.timeout)
            .POST(HttpRequest.BodyPublishers.ofString(request.body))
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return try {
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            RecommendationHttpResponse(response.statusCode(), response.headers().map(), response.body())
        } catch (_: java.net.http.HttpTimeoutException) {
            throw RecommendationProviderException(RecommendationFailureCategory.TIMEOUT)
        } catch (_: java.io.IOException) {
            throw RecommendationProviderException(RecommendationFailureCategory.NETWORK_ERROR)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RecommendationProviderException(RecommendationFailureCategory.NETWORK_ERROR)
        }
    }
}

object RecommendationSmokeFixtureScope {
    private val fixtureMode = ThreadLocal<RecommendationSmokeFixtureMode?>()

    fun <T> withFixture(mode: RecommendationSmokeFixtureMode?, block: () -> T): T {
        val previous = fixtureMode.get()
        fixtureMode.set(mode)
        return try {
            block()
        } finally {
            fixtureMode.set(previous)
        }
    }

    internal fun current(): RecommendationSmokeFixtureMode? = fixtureMode.get()
}

@Component
class SmokeAwareRecommendationHttpClient : RecommendationHttpClient {
    private val delegate = JdkRecommendationHttpClient()

    override fun postJson(request: RecommendationHttpRequest): RecommendationHttpResponse =
        when (RecommendationSmokeFixtureScope.current()) {
            RecommendationSmokeFixtureMode.SUCCESS -> RecommendationHttpResponse(200, emptyMap(), openAiFixture(successItem()))
            RecommendationSmokeFixtureMode.EMPTY_SUCCESS -> RecommendationHttpResponse(200, emptyMap(), openAiFixture(""))
            RecommendationSmokeFixtureMode.MALFORMED_RESPONSE -> RecommendationHttpResponse(200, emptyMap(), """{"choices":[{"message":{"content":"{}"}}]}""")
            RecommendationSmokeFixtureMode.TIMEOUT -> throw RecommendationProviderException(RecommendationFailureCategory.TIMEOUT)
            RecommendationSmokeFixtureMode.RATE_LIMITED -> RecommendationHttpResponse(429, mapOf("retry-after" to listOf("1")), "")
            RecommendationSmokeFixtureMode.AUTHENTICATION_FAILURE -> RecommendationHttpResponse(401, emptyMap(), "")
            RecommendationSmokeFixtureMode.PROVIDER_UNAVAILABLE -> RecommendationHttpResponse(503, emptyMap(), "")
            null -> if (request.endpoint.host in setOf("localhost", "127.0.0.1") && request.endpoint.path.contains("/stub")) {
                RecommendationHttpResponse(200, emptyMap(), openAiFixture(successItem()))
            } else {
                delegate.postJson(request)
            }
        }

    private fun openAiFixture(recommendations: String): String {
        val content = if (recommendations.isBlank()) {
            """{"recommendations":[]}"""
        } else {
            """{"recommendations":[$recommendations]}"""
        }
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"choices":[{"message":{"content":"$escaped"}}]}"""
    }

    private fun successItem(): String =
        """{"category":"ARRIVAL_RISK_REVIEW","priority":"MEDIUM","confidence":"MEDIUM","explanation":{"situation":"Synthetic arrival preparation signal","rationale":"The smoke context has an upcoming confirmed stay and low backlog","supportingSignals":["synthetic_context","arrival_proximity_band"]},"intentType":"HOUSEKEEPING","proposedTaskTitle":"Review synthetic arrival readiness","proposedTaskSummary":"Validate that the recommendation provider can return structured task advice."}"""
}

class RecommendationProviderException(
    val failureCategory: RecommendationFailureCategory
) : RuntimeException("Recommendation provider failed with category ${failureCategory.name}.")
