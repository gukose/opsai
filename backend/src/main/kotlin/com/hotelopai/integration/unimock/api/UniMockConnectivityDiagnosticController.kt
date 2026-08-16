package com.hotelopai.integration.unimock.api

import com.hotelopai.integration.unimock.UniMockClientProperties
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.measureTimeMillis

@RestController
@Profile("demo")
@RequestMapping("/api/v1/internal/pms/unimock/connectivity")
class UniMockConnectivityDiagnosticController(
    private val properties: UniMockClientProperties,
    private val environment: Environment
) {
    @GetMapping
    @PreAuthorize(PermissionExpressions.PMS_OPERATIONS_ACCESS)
    fun diagnose(): UniMockConnectivityDiagnosticResponse {
        val configuredUri = URI.create(properties.baseUrl)
        require(configuredUri.userInfo == null) { "UniMock base URL must not contain credentials" }
        val port = configuredUri.effectivePort()
        val host = requireNotNull(configuredUri.host) { "UniMock base URL must contain a host" }

        val dns = dns(host)
        val tcp = tcp(host, port)
        val health = health(configuredUri, host, port)

        return UniMockConnectivityDiagnosticResponse(
            configuredOrigin = "${configuredUri.scheme}://$host:$port",
            host = host,
            port = port,
            dns = dns,
            tcp = tcp,
            health = health,
            railway = RailwayDeploymentIdentity(
                projectId = environment.getProperty("RAILWAY_PROJECT_ID"),
                environmentId = environment.getProperty("RAILWAY_ENVIRONMENT_ID"),
                serviceId = environment.getProperty("RAILWAY_SERVICE_ID")
            )
        )
    }

    private fun dns(host: String): DnsDiagnostic {
        var families = emptyList<String>()
        var failureType: String? = null
        val elapsed = measureTimeMillis {
            try {
                families = InetAddress.getAllByName(host)
                    .mapNotNull {
                        when (it) {
                            is Inet6Address -> "IPv6"
                            is Inet4Address -> "IPv4"
                            else -> null
                        }
                    }
                    .distinct()
                    .sorted()
            } catch (exception: Exception) {
                failureType = exception.javaClass.simpleName
            }
        }
        return DnsDiagnostic(failureType == null && families.isNotEmpty(), families, elapsed, failureType)
    }

    private fun tcp(host: String, port: Int): ProbeDiagnostic {
        var success = false
        var failureType: String? = null
        val elapsed = measureTimeMillis {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MILLIS)
                    success = true
                }
            } catch (exception: Exception) {
                failureType = exception.javaClass.simpleName
            }
        }
        return ProbeDiagnostic(success, elapsed, failureType)
    }

    private fun health(configuredUri: URI, host: String, port: Int): HttpDiagnostic {
        var status: Int? = null
        var failureType: String? = null
        val elapsed = measureTimeMillis {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("${configuredUri.scheme}://$host:$port/actuator/health"))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build()
                status = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding())
                    .statusCode()
            } catch (exception: Exception) {
                failureType = exception.javaClass.simpleName
            }
        }
        return HttpDiagnostic(status != null, status, elapsed, failureType)
    }

    private fun URI.effectivePort(): Int =
        when {
            port >= 0 -> port
            scheme.equals("https", ignoreCase = true) -> 443
            else -> 80
        }

    private companion object {
        const val TCP_TIMEOUT_MILLIS = 3_000
        val HTTP_TIMEOUT: Duration = Duration.ofSeconds(4)
    }
}

data class UniMockConnectivityDiagnosticResponse(
    val configuredOrigin: String,
    val host: String,
    val port: Int,
    val dns: DnsDiagnostic,
    val tcp: ProbeDiagnostic,
    val health: HttpDiagnostic,
    val railway: RailwayDeploymentIdentity
)

data class DnsDiagnostic(
    val success: Boolean,
    val addressFamilies: List<String>,
    val elapsedMilliseconds: Long,
    val failureType: String?
)

data class ProbeDiagnostic(
    val success: Boolean,
    val elapsedMilliseconds: Long,
    val failureType: String?
)

data class HttpDiagnostic(
    val success: Boolean,
    val status: Int?,
    val elapsedMilliseconds: Long,
    val failureType: String?
)

data class RailwayDeploymentIdentity(
    val projectId: String?,
    val environmentId: String?,
    val serviceId: String?
)
