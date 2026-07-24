package com.hotelopai.integration.apaleo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.OAuth2ClientCredentialsPmsAuthenticationProperties
import com.hotelopai.pms.application.PmsAuthenticationMode
import com.hotelopai.pms.application.PmsAuthenticationProperties
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.PmsCircuitState
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsCredentialResolver
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsHealthState
import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsProviderProperties
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.application.UnsupportedPmsCapabilityException
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderAuthenticationException
import com.hotelopai.pms.domain.PmsProviderCircuitOpenException
import com.hotelopai.pms.domain.PmsProviderConfigurationFailureException
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsProviderInvalidRequestException
import com.hotelopai.pms.domain.PmsProviderMalformedResponseException
import com.hotelopai.pms.domain.PmsProviderPermissionException
import com.hotelopai.pms.domain.PmsProviderRateLimitException
import com.hotelopai.pms.domain.PmsProviderResourceNotFoundException
import com.hotelopai.pms.domain.PmsProviderTimeoutException
import com.hotelopai.pms.domain.PmsProviderUnavailableException
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.support.MockHttpResponse
import com.hotelopai.support.MockHttpServer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ApaleoPmsAdapterTest {
    private val server = MockHttpServer()
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val credentials = StaticCredentialResolver(
        mapOf(
            "env:APALEO_CLIENT_ID" to "client-id",
            "env:APALEO_CLIENT_SECRET" to "client-secret"
        )
    )

    @BeforeEach
    fun startServer() {
        server.start()
        server.stub("POST", "/connect/token", tokenResponse())
    }

    @AfterEach
    fun stopServer() {
        server.stop()
    }

    @Test
    fun `client sends OAuth token request and bearer API request without exposing credentials`() {
        server.stub("GET", "/inventory/v1/units", unitListResponse())

        val rooms = client().listUnits()

        assertEquals("101", rooms.single().name)
        val tokenRequest = server.lastRequest("POST", "/connect/token")!!
        val expectedBasic = Base64.getEncoder().encodeToString("client-id:client-secret".toByteArray())
        assertEquals(listOf("Basic $expectedBasic"), tokenRequest.headers["authorization"])
        assertThat(tokenRequest.body).contains("grant_type=client_credentials")
        assertThat(tokenRequest.body).contains("scope=")

        val apiRequest = server.lastRequest("GET", "/inventory/v1/units")!!
        assertEquals(listOf("Bearer sandbox-token"), apiRequest.headers["authorization"])
        assertThat(apiRequest.query).contains("pageNumber=1", "pageSize=100", "propertyIds=MUC")
        assertThat(tokenRequest.toString()).doesNotContain("client-secret")
        assertThat(apiRequest.toString()).doesNotContain("client-secret")
    }

    @Test
    fun `provider maps hotel room reservation guest and stay responses`() {
        server.stub("GET", "/inventory/v1/properties/MUC", propertyResponse())
        server.stub("GET", "/inventory/v1/units", unitListResponse())
        server.stub("GET", "/booking/v1/bookings", bookingListResponse())
        val provider = provider()

        val hotel = provider.getHotel("MUC")!!
        val room = provider.listRooms().single()
        val reservation = provider.listReservations().single()
        val guest = provider.listGuests().first { it.fullName == "Ada Lovelace" }
        val stay = provider.findStay("101")!!

        assertEquals("MUC", hotel.hotelId)
        assertEquals("Demo Hotel Munich", hotel.name)
        assertEquals("101", room.number)
        assertEquals("MUC-DBL", room.roomTypeId)
        assertEquals("RES-1", reservation.id)
        assertEquals("2026-07-24T17:00:00+02:00", reservation.arrivalDate)
        assertEquals("guest-1", guest.id)
        assertEquals("RES-1", stay.reservationId)
        assertEquals(true, stay.occupied)
    }

    @Test
    fun `pagination is bounded by configured max pages`() {
        server.stub(
            "GET",
            "/inventory/v1/units",
            unitListResponse(count = 10)
        )

        val rooms = client(pageSize = 1, maxPages = 2).listUnits()

        assertEquals(2, rooms.size)
        assertEquals(2, server.requests("GET", "/inventory/v1/units").size)
    }

    @Test
    fun `provider declares only implemented read capabilities`() {
        val provider = provider()

        assertEquals(true, provider.capabilities.hotelLookup)
        assertEquals(true, provider.capabilities.roomListing)
        assertEquals(true, provider.capabilities.reservationLookup)
        assertEquals(true, provider.capabilities.guestLookup)
        assertEquals(true, provider.capabilities.stayLookup)
        assertEquals(false, provider.capabilities.maintenanceUpdate)

        assertThrows(UnsupportedPmsCapabilityException::class.java) {
            provider.updateMaintenance(MaintenanceUpdate("101", "LEAK", "Leak"))
        }
    }

    @Test
    fun `registry can activate Apaleo while default remains internal demo`() {
        val defaultRegistry = PmsProviderRegistry(
            listOf(StubProvider("internal-demo"), provider()),
            PmsProviderProperties()
        )
        assertEquals("internal-demo", defaultRegistry.activeProvider().id.value)

        val apaleoRegistry = PmsProviderRegistry(
            listOf(StubProvider("internal-demo"), provider()),
            PmsProviderProperties(
                activeProvider = "apaleo",
                providers = mapOf(
                    "internal-demo" to PmsConfiguredProviderProperties(enabled = true),
                    "apaleo" to apaleoConfig()
                )
            )
        )
        assertEquals("apaleo", apaleoRegistry.activeProvider().id.value)
    }

    @Test
    fun `unresolved credential reference fails without leaking values`() {
        val exception = assertThrows(PmsProviderConfigurationFailureException::class.java) {
            client(credentialResolver = StaticCredentialResolver(emptyMap())).listUnits()
        }

        assertThat(exception.message).contains("env:APALEO_CLIENT_ID")
        assertThat(exception.message).doesNotContain("client-secret")
    }

    @Test
    fun `provider status errors map to provider neutral exceptions`() {
        assertStatusMapsTo(400, PmsProviderInvalidRequestException::class.java)
        assertStatusMapsTo(401, PmsProviderAuthenticationException::class.java)
        assertStatusMapsTo(403, PmsProviderPermissionException::class.java)
        assertStatusMapsTo(404, PmsProviderResourceNotFoundException::class.java)
        assertStatusMapsTo(429, PmsProviderRateLimitException::class.java)
        assertStatusMapsTo(503, PmsProviderUnavailableException::class.java)
    }

    @Test
    fun `malformed provider response maps to neutral malformed response exception`() {
        server.stub("GET", "/inventory/v1/units", MockHttpResponse(200, """{"units":"""))

        assertThrows(PmsProviderMalformedResponseException::class.java) {
            client().listUnits()
        }
    }

    @Test
    fun `timeout maps to provider neutral timeout exception`() {
        server.stub("GET", "/inventory/v1/units", MockHttpResponse(200, """{"units":[]}""", delayMs = 250))

        assertThrows(PmsProviderTimeoutException::class.java) {
            client(timeout = Duration.ofMillis(50)).listUnits()
        }
    }

    @Test
    fun `metrics use safe provider operation outcome and status tags`() {
        val registry = SimpleMeterRegistry()
        server.stub("GET", "/inventory/v1/units", unitListResponse())

        client(observability = OperationalObservability(registry)).listUnits()

        assertEquals(
            1.0,
            registry.counter(
                "pms_provider_requests_total",
                "provider",
                "apaleo",
                "operation",
                "list_units",
                "outcome",
                "success",
                "status",
                "200"
            ).count()
        )
    }

    @Test
    fun `health reports ready Apaleo response and safe diagnostics`() {
        server.stub("GET", "/inventory/v1/properties/MUC", propertyResponse())
        val meterRegistry = SimpleMeterRegistry()
        val apaleoProvider = provider(observability = OperationalObservability(meterRegistry))
        val registry = PmsProviderRegistry(
            listOf(StubProvider("internal-demo"), apaleoProvider),
            PmsProviderProperties(
                activeProvider = "apaleo",
                providers = mapOf(
                    "internal-demo" to PmsConfiguredProviderProperties(enabled = true),
                    "apaleo" to apaleoConfig()
                )
            )
        )

        val health = apaleoProvider.health(apaleoConfig())
        val diagnostic = registry.activeProviderDiagnostic()

        assertEquals(PmsHealthState.READY, health.state)
        assertEquals(PmsCircuitState.CLOSED, health.circuitState)
        assertEquals(PmsHealthState.READY, diagnostic.healthState)
        assertEquals(PmsCircuitState.CLOSED, diagnostic.circuitState)
        assertThat(diagnostic.toString()).doesNotContain("client-secret")
        assertEquals(
            2.0,
            meterRegistry.counter(
                "pms_provider_health_checks_total",
                "provider",
                "apaleo",
                "operation",
                "health_check",
                "outcome",
                "ready",
                "status",
                "none"
            ).count()
        )
    }

    @Test
    fun `health reports misconfigured provider without network call`() {
        val health = provider(credentialResolver = StaticCredentialResolver(emptyMap()))
            .health(apaleoConfig())

        assertEquals(PmsHealthState.MISCONFIGURED, health.state)
        assertEquals(PmsFailureCategory.CONFIGURATION, health.failureCategory)
        assertEquals(0, server.requests("GET", "/inventory/v1/properties/MUC").size)
    }

    @Test
    fun `transient timeout retries then succeeds without real waiting`() {
        val sleeper = RecordingSleeper()
        server.stubSequence(
            "GET",
            "/inventory/v1/units",
            listOf(
                MockHttpResponse(200, """{"units":[]}""", delayMs = 250),
                unitListResponse()
            )
        )

        val rooms = client(timeout = Duration.ofMillis(50), sleeper = sleeper, retryMaxAttempts = 2).listUnits()

        assertEquals("101", rooms.single().name)
        assertEquals(1, sleeper.sleeps.size)
    }

    @Test
    fun `transient 5xx retries then succeeds`() {
        val sleeper = RecordingSleeper()
        server.stubSequence(
            "GET",
            "/inventory/v1/units",
            listOf(
                MockHttpResponse(503, """{"message":"temporarily unavailable"}"""),
                unitListResponse()
            )
        )

        val rooms = client(sleeper = sleeper, retryMaxAttempts = 2).listUnits()

        assertEquals("101", rooms.single().name)
        assertEquals(2, server.requests("GET", "/inventory/v1/units").size)
        assertEquals(listOf(Duration.ofMillis(100)), sleeper.sleeps)
    }

    @Test
    fun `retry exhaustion preserves provider neutral exception`() {
        server.stub("GET", "/inventory/v1/units", MockHttpResponse(503, """{"message":"unavailable"}"""))

        assertThrows(PmsProviderUnavailableException::class.java) {
            client(retryMaxAttempts = 2).listUnits()
        }

        assertEquals(2, server.requests("GET", "/inventory/v1/units").size)
    }

    @Test
    fun `non retryable provider errors are not retried`() {
        listOf(400, 403, 404).forEach { status ->
            server.reset()
            server.stub("POST", "/connect/token", tokenResponse())
            server.stub("GET", "/inventory/v1/units", MockHttpResponse(status, """{"message":"do not retry"}"""))

            assertThrows(RuntimeException::class.java) {
                client(retryMaxAttempts = 3).listUnits()
            }

            assertEquals(1, server.requests("GET", "/inventory/v1/units").size)
        }
    }

    @Test
    fun `cached token is reused before expiry`() {
        server.stub("GET", "/inventory/v1/units", unitListResponse())
        val tokenCache = ApaleoOAuthTokenCache()
        val client = client(tokenCache = tokenCache)

        client.listUnits()
        client.listUnits()

        assertEquals(1, server.requests("POST", "/connect/token").size)
        assertEquals(2, server.requests("GET", "/inventory/v1/units").size)
    }

    @Test
    fun `cached token refreshes near expiry`() {
        server.reset()
        server.stubSequence(
            "POST",
            "/connect/token",
            listOf(tokenResponse(accessToken = "token-one", expiresIn = 30), tokenResponse(accessToken = "token-two"))
        )
        server.stub("GET", "/inventory/v1/units", unitListResponse())
        val client = client(tokenExpirySafetyWindow = Duration.ofSeconds(60))

        client.listUnits()
        client.listUnits()

        assertEquals(2, server.requests("POST", "/connect/token").size)
        assertEquals(listOf("Bearer token-two"), server.lastRequest("GET", "/inventory/v1/units")!!.headers["authorization"])
    }

    @Test
    fun `concurrent calls share one token acquisition`() {
        server.stub("GET", "/inventory/v1/units", unitListResponse())
        val client = client(tokenCache = ApaleoOAuthTokenCache())
        val executor = Executors.newFixedThreadPool(4)

        repeat(4) {
            executor.submit { client.listUnits() }
        }
        executor.shutdown()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        assertEquals(1, server.requests("POST", "/connect/token").size)
        assertEquals(4, server.requests("GET", "/inventory/v1/units").size)
    }

    @Test
    fun `api authentication rejection invalidates token and reacquires once`() {
        server.reset()
        server.stubSequence(
            "POST",
            "/connect/token",
            listOf(tokenResponse(accessToken = "stale-token"), tokenResponse(accessToken = "fresh-token"))
        )
        server.stubSequence(
            "GET",
            "/inventory/v1/units",
            listOf(MockHttpResponse(401, ""), unitListResponse())
        )

        val rooms = client().listUnits()

        assertEquals("101", rooms.single().name)
        assertEquals(2, server.requests("POST", "/connect/token").size)
        assertEquals(2, server.requests("GET", "/inventory/v1/units").size)
        assertEquals(listOf("Bearer fresh-token"), server.lastRequest("GET", "/inventory/v1/units")!!.headers["authorization"])
    }

    @Test
    fun `rate limit Retry-After is honored only within configured bound`() {
        val sleeper = RecordingSleeper()
        server.stubSequence(
            "GET",
            "/inventory/v1/units",
            listOf(
                MockHttpResponse(
                    status = 429,
                    body = "",
                    headers = mapOf("Retry-After" to "1")
                ),
                unitListResponse()
            )
        )

        val rooms = client(sleeper = sleeper, retryMaxAttempts = 2, maxRateLimitDelay = Duration.ofSeconds(2)).listUnits()

        assertEquals("101", rooms.single().name)
        assertEquals(listOf(Duration.ofSeconds(1)), sleeper.sleeps)
    }

    @Test
    fun `excessive Retry-After fails fast without waiting`() {
        val sleeper = RecordingSleeper()
        server.stub(
            "GET",
            "/inventory/v1/units",
            MockHttpResponse(
                status = 429,
                body = "",
                headers = mapOf("Retry-After" to "60")
            )
        )

        assertThrows(PmsProviderRateLimitException::class.java) {
            client(sleeper = sleeper, retryMaxAttempts = 2, maxRateLimitDelay = Duration.ofSeconds(2)).listUnits()
        }

        assertEquals(emptyList<Duration>(), sleeper.sleeps)
        assertEquals(1, server.requests("GET", "/inventory/v1/units").size)
    }

    @Test
    fun `circuit opens rejects calls and half-open success closes`() {
        val clock = MutableClock()
        val circuit = ApaleoCircuitBreaker("apaleo", OperationalObservability.noop(), clock)
        server.stub("GET", "/inventory/v1/units", MockHttpResponse(503, """{"message":"unavailable"}"""))

        assertThrows(PmsProviderUnavailableException::class.java) {
            client(circuitBreaker = circuit, retryMaxAttempts = 1, circuitFailureThreshold = 1).listUnits()
        }
        assertEquals(PmsCircuitState.OPEN, circuit.state(circuitPolicy()))

        assertThrows(PmsProviderCircuitOpenException::class.java) {
            client(circuitBreaker = circuit, retryMaxAttempts = 1, circuitFailureThreshold = 1).listUnits()
        }

        clock.advance(Duration.ofMillis(101))
        server.stub("GET", "/inventory/v1/units", unitListResponse())
        val rooms = client(circuitBreaker = circuit, retryMaxAttempts = 1, circuitFailureThreshold = 1).listUnits()

        assertEquals("101", rooms.single().name)
        assertEquals(PmsCircuitState.CLOSED, circuit.state(circuitPolicy()))
    }

    @Test
    fun `half-open failure reopens circuit`() {
        val clock = MutableClock()
        val circuit = ApaleoCircuitBreaker("apaleo", OperationalObservability.noop(), clock)
        server.stub("GET", "/inventory/v1/units", MockHttpResponse(503, """{"message":"unavailable"}"""))

        assertThrows(PmsProviderUnavailableException::class.java) {
            client(circuitBreaker = circuit, retryMaxAttempts = 1, circuitFailureThreshold = 1).listUnits()
        }
        clock.advance(Duration.ofMillis(101))

        assertThrows(PmsProviderUnavailableException::class.java) {
            client(circuitBreaker = circuit, retryMaxAttempts = 1, circuitFailureThreshold = 1).listUnits()
        }

        assertEquals(PmsCircuitState.OPEN, circuit.state(circuitPolicy()))
    }

    private fun assertStatusMapsTo(status: Int, expected: Class<out Throwable>) {
        server.reset()
        server.stub("POST", "/connect/token", tokenResponse())
        server.stub(
            "GET",
            "/inventory/v1/units",
            MockHttpResponse(
                status = status,
                body = """{"message":"safe failure"}"""
            )
        )

        val exception = assertThrows(expected) {
            client().listUnits()
        }

        assertThat(exception.message).contains("status $status")
        assertThat(exception.message).doesNotContain("safe failure")
    }

    private fun provider(
        credentialResolver: PmsCredentialResolver = credentials,
        observability: OperationalObservability = OperationalObservability.noop()
    ): ApaleoPmsProvider =
        ApaleoPmsProvider(
            properties = PmsProviderProperties(
                activeProvider = "apaleo",
                providers = mapOf(
                    "internal-demo" to PmsConfiguredProviderProperties(enabled = true),
                    "apaleo" to apaleoConfig()
                )
            ),
            objectMapper = objectMapper,
            credentialResolver = credentialResolver,
            observability = observability
        )

    private fun client(
        pageSize: Int = 100,
        maxPages: Int = 2,
        timeout: Duration = Duration.ofSeconds(2),
        credentialResolver: PmsCredentialResolver = credentials,
        observability: OperationalObservability = OperationalObservability.noop(),
        sleeper: ApaleoSleeper = RecordingSleeper(),
        circuitBreaker: ApaleoCircuitBreaker = ApaleoCircuitBreaker("apaleo", observability),
        tokenCache: ApaleoOAuthTokenCache = ApaleoOAuthTokenCache(),
        retryMaxAttempts: Int = 2,
        maxRateLimitDelay: Duration = Duration.ofSeconds(2),
        circuitFailureThreshold: Int = 3,
        tokenExpirySafetyWindow: Duration = Duration.ofSeconds(60)
    ): ApaleoPmsHttpClient =
        ApaleoPmsHttpClient(
            config = apaleoConfig(
                pageSize = pageSize,
                maxPages = maxPages,
                timeout = timeout,
                retryMaxAttempts = retryMaxAttempts,
                maxRateLimitDelay = maxRateLimitDelay,
                circuitFailureThreshold = circuitFailureThreshold,
                tokenExpirySafetyWindow = tokenExpirySafetyWindow
            ),
            objectMapper = objectMapper,
            credentialResolver = credentialResolver,
            observability = observability,
            circuitBreaker = circuitBreaker,
            tokenCache = tokenCache,
            sleeper = sleeper
        )

    private fun apaleoConfig(
        pageSize: Int = 100,
        maxPages: Int = 2,
        timeout: Duration = Duration.ofSeconds(2),
        retryMaxAttempts: Int = 2,
        maxRateLimitDelay: Duration = Duration.ofSeconds(2),
        circuitFailureThreshold: Int = 3,
        tokenExpirySafetyWindow: Duration = Duration.ofSeconds(60)
    ): PmsConfiguredProviderProperties =
        PmsConfiguredProviderProperties(
            enabled = true,
            endpoint = server.baseUrl,
            hotelPropertyIdentifier = "MUC",
            requestTimeout = timeout,
            authentication = PmsAuthenticationProperties(
                mode = PmsAuthenticationMode.OAUTH2_CLIENT_CREDENTIALS,
                oauth2ClientCredentials = OAuth2ClientCredentialsPmsAuthenticationProperties(
                    tokenUrl = "${server.baseUrl}/connect/token",
                    clientIdReference = "env:APALEO_CLIENT_ID",
                    clientSecretReference = "env:APALEO_CLIENT_SECRET",
                    scopes = listOf("properties.read", "units.read", "reservations.read")
                )
            ),
            settings = mapOf(
                "page-size" to pageSize.toString(),
                "max-pages" to maxPages.toString(),
                "retry-max-attempts" to retryMaxAttempts.toString(),
                "retry-initial-backoff" to "PT0.1S",
                "retry-max-backoff" to "PT1S",
                "max-rate-limit-delay" to maxRateLimitDelay.toString(),
                "circuit-failure-threshold" to circuitFailureThreshold.toString(),
                "circuit-open-duration" to "PT0.1S",
                "circuit-half-open-max-attempts" to "1",
                "token-expiry-safety-window" to tokenExpirySafetyWindow.toString()
            )
        )

    private fun circuitPolicy(): ApaleoCircuitBreakerPolicy =
        ApaleoCircuitBreakerPolicy(
            failureThreshold = 1,
            openDuration = Duration.ofMillis(100),
            halfOpenMaxAttempts = 1
        )

    private fun tokenResponse(
        accessToken: String = "sandbox-token",
        expiresIn: Long = 3600
    ): MockHttpResponse =
        MockHttpResponse(
            status = 200,
            body = """{"access_token":"$accessToken","expires_in":$expiresIn,"token_type":"Bearer","scope":"properties.read units.read reservations.read"}"""
        )

    private fun propertyResponse(): MockHttpResponse =
        MockHttpResponse(
            status = 200,
            body = """{"id":"MUC","code":"MUC","name":"Demo Hotel Munich"}"""
        )

    private fun unitListResponse(count: Int = 1): MockHttpResponse =
        MockHttpResponse(
            status = 200,
            body = """{"units":[{"id":"MUC-101","name":"101","condition":"Clean","unitGroup":{"id":"MUC-DBL","name":"Double Room"}}],"count":$count}"""
        )

    private fun bookingListResponse(): MockHttpResponse =
        MockHttpResponse(
            status = 200,
            body = """
                {
                  "bookings": [
                    {
                      "id": "BOOK-1",
                      "booker": {"firstName": "Grace", "lastName": "Hopper", "email": "grace@example.test"},
                      "reservations": [
                        {
                          "id": "RES-1",
                          "status": "InHouse",
                          "arrival": "2026-07-24T17:00:00+02:00",
                          "departure": "2026-07-26T11:00:00+02:00",
                          "unit": {"id": "MUC-101", "name": "101"},
                          "primaryGuest": {"id": "guest-1", "firstName": "Ada", "lastName": "Lovelace"}
                        }
                      ]
                    }
                  ],
                  "count": 1
                }
            """.trimIndent()
        )

    private class StaticCredentialResolver(
        private val values: Map<String, String>
    ) : PmsCredentialResolver {
        override fun resolve(reference: String): String =
            values[reference] ?: throw PmsProviderConfigurationFailureException(
                "PMS credential reference '$reference' could not be resolved."
            )
    }

    private class RecordingSleeper : ApaleoSleeper {
        val sleeps = mutableListOf<Duration>()
        override fun sleep(duration: Duration) {
            sleeps += duration
        }
    }

    private class MutableClock : Clock() {
        private var current = Instant.parse("2026-07-24T00:00:00Z")
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private class StubProvider(
        id: String
    ) : PmsProvider {
        override val id = PmsProviderId(id)
        override val displayName = "Stub"
        override val capabilities = PmsCapabilities(roomListing = true)
        override fun listRooms(): List<PmsRoom> = emptyList()
        override fun findRoom(roomNumber: String): PmsRoom? = null
        override fun findRoomStatus(roomNumber: String): PmsRoomStatus? = null
        override fun findStay(roomNumber: String): PmsStay? = null
        override fun getRoomAssets(roomNumber: String): List<PmsAsset> = emptyList()
        override fun findAsset(assetId: String): PmsAsset? = null
        override fun listIssueTypes(): List<PmsIssueType> = emptyList()
        override fun findHousekeepingTask(taskId: String): PmsHousekeepingTask? = null
        override fun updateHousekeepingTaskStatus(
            taskId: String,
            request: HousekeepingTaskStatusUpdate
        ): PmsHousekeepingTask = error("not used")

        override fun updateRoomStatus(
            roomNumber: String,
            request: RoomStatusUpdate
        ): PmsUpdateResult = PmsUpdateResult(UUID.randomUUID(), roomNumber, "ROOM_STATUS_UPDATE", request.status)

        override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult =
            PmsUpdateResult(UUID.randomUUID(), request.roomNumber, "MAINTENANCE_UPDATE", request.status)

        override fun createEvent(command: PmsEventCreateCommand): PmsEvent =
            PmsEvent(command.eventId ?: "event-1", command.type, command.subject)
    }
}
