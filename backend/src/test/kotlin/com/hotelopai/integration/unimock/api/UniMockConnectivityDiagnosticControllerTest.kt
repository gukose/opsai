package com.hotelopai.integration.unimock.api

import com.hotelopai.integration.unimock.UniMockClientProperties
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.net.InetSocketAddress

class UniMockConnectivityDiagnosticControllerTest {
    @Test
    fun `diagnostic proves DNS TCP and health without exposing response content`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/actuator/health") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()

        try {
            val environment = MockEnvironment()
                .withProperty("RAILWAY_PROJECT_ID", "project-id")
                .withProperty("RAILWAY_ENVIRONMENT_ID", "environment-id")
                .withProperty("RAILWAY_SERVICE_ID", "backend-service-id")
            val controller = UniMockConnectivityDiagnosticController(
                UniMockClientProperties(baseUrl = "http://127.0.0.1:${server.address.port}"),
                environment
            )

            val result = controller.diagnose()

            assertThat(result.configuredOrigin).isEqualTo("http://127.0.0.1:${server.address.port}")
            assertThat(result.dns.success).isTrue()
            assertThat(result.dns.addressFamilies).containsExactly("IPv4")
            assertThat(result.tcp.success).isTrue()
            assertThat(result.health.success).isTrue()
            assertThat(result.health.status).isEqualTo(200)
            assertThat(result.railway.projectId).isEqualTo("project-id")
            assertThat(result.toString()).doesNotContain("UP")
        } finally {
            server.stop(0)
        }
    }
}
