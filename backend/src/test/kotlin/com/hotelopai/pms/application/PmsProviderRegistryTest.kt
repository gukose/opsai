package com.hotelopai.pms.application

import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.util.UUID

class PmsProviderRegistryTest {
    @Test
    fun `registry resolves configured active provider`() {
        val first = StubPmsProvider("internal-demo")
        val second = StubPmsProvider("future-provider")
        val registry = PmsProviderRegistry(
            listOf(first, second),
            properties(
                activeProvider = "future-provider",
                providerIds = listOf("internal-demo", "future-provider")
            )
        )

        assertEquals(second, registry.activeProvider())
        assertEquals(listOf("future-provider", "internal-demo"), registry.registeredProviderIds())
    }

    @Test
    fun `registry fails for missing provider`() {
        val registry = PmsProviderRegistry(
            listOf(StubPmsProvider("internal-demo")),
            properties(activeProvider = "internal-demo")
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            registry.provider(PmsProviderId("missing"))
        }

        assertEquals(true, exception.message!!.contains("No PMS provider registered"))
    }

    @Test
    fun `registry rejects missing active provider during startup`() {
        val exception = assertThrows(PmsProviderConfigurationException::class.java) {
            PmsProviderRegistry(
                listOf(StubPmsProvider("internal-demo")),
                properties(
                    activeProvider = "future-provider",
                    providerIds = listOf("internal-demo", "future-provider")
                )
            )
        }

        assertTrue(exception.message!!.contains("future-provider"))
        assertTrue(exception.message!!.contains("No PMS provider registered"))
    }

    @Test
    fun `registry rejects duplicate provider ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            PmsProviderRegistry(
                listOf(StubPmsProvider("internal-demo"), StubPmsProvider("internal-demo")),
                properties()
            )
        }
    }

    @Test
    fun `registry rejects disabled active provider during startup`() {
        val exception = assertThrows(PmsProviderConfigurationException::class.java) {
            PmsProviderRegistry(
                listOf(StubPmsProvider("internal-demo")),
                properties(enabled = false)
            )
        }

        assertTrue(exception.message!!.contains("disabled"))
    }

    @Test
    fun `registry rejects registered provider without configuration`() {
        val exception = assertThrows(PmsProviderConfigurationException::class.java) {
            PmsProviderRegistry(
                listOf(StubPmsProvider("internal-demo"), StubPmsProvider("future-provider")),
                properties(activeProvider = "internal-demo")
            )
        }

        assertTrue(exception.message!!.contains("future-provider"))
        assertTrue(exception.message!!.contains("no configuration entry"))
    }

    @Test
    fun `registry rejects inconsistent capabilities`() {
        val exception = assertThrows(PmsProviderConfigurationException::class.java) {
            PmsProviderRegistry(
                listOf(
                    StubPmsProvider(
                        id = "internal-demo",
                        capabilities = PmsCapabilities(maintenanceUpdate = true)
                    )
                ),
                properties()
            )
        }

        assertTrue(exception.message!!.contains("maintenance update"))
    }

    @Test
    fun `active provider requiring fails when capability is unsupported`() {
        val registry = PmsProviderRegistry(
            listOf(StubPmsProvider("internal-demo", capabilities = PmsCapabilities(roomListing = true))),
            properties()
        )

        assertThrows(UnsupportedPmsCapabilityException::class.java) {
            registry.activeProviderRequiring(PmsCapability.MAINTENANCE_UPDATE)
        }
    }

    @Test
    fun `diagnostics expose safe configuration summary`() {
        val registry = PmsProviderRegistry(
            listOf(StubPmsProvider("internal-demo")),
            PmsProviderProperties(
                providers = mapOf(
                    "internal-demo" to PmsConfiguredProviderProperties(
                        enabled = true,
                        displayName = "Internal Demo PMS",
                        endpoint = "https://pms.example.test/api",
                        authentication = PmsAuthenticationProperties(
                            mode = PmsAuthenticationMode.API_KEY,
                            apiKey = ApiKeyPmsAuthenticationProperties(
                                headerName = "X-Api-Key",
                                credentialReference = "secret://pms/internal-demo/api-key"
                            )
                        ),
                        settings = mapOf("tenant" to "demo", "secret" to "should-not-leak")
                    )
                )
            )
        )

        val diagnostic = registry.activeProviderDiagnostic()

        assertEquals("pms.example.test", diagnostic.endpointHost)
        assertEquals(PmsAuthenticationMode.API_KEY, diagnostic.authentication.mode)
        assertEquals(listOf("secret://pms/internal-demo/api-key"), diagnostic.authentication.credentialReferences)
        assertEquals(listOf("secret", "tenant"), diagnostic.settingsKeys)
        assertEquals(false, diagnostic.toString().contains("should-not-leak"))
    }

    @Test
    fun `production guard rejects active external provider without approval`() {
        val exception = assertThrows(PmsProviderConfigurationException::class.java) {
            PmsProviderRegistry(
                listOf(StubPmsProvider("internal-demo"), StubPmsProvider("apaleo")),
                PmsProviderProperties(
                    activeProvider = "apaleo",
                    providers = mapOf(
                        "internal-demo" to PmsConfiguredProviderProperties(enabled = true),
                        "apaleo" to PmsConfiguredProviderProperties(
                            enabled = true,
                            productionApproved = false
                        )
                    )
                ),
                MockEnvironment().withProperty("spring.profiles.active", "prod")
            )
        }

        assertTrue(exception.message!!.contains("production approval"))
        assertEquals(false, exception.message!!.contains("secret"))
    }

    @Test
    fun `production guard exempts internal demo provider`() {
        val registry = PmsProviderRegistry(
            listOf(StubPmsProvider("internal-demo")),
            properties(activeProvider = "internal-demo"),
            MockEnvironment().withProperty("spring.profiles.active", "prod")
        )

        assertEquals("internal-demo", registry.activeProvider().id.value)
    }

    private fun properties(
        activeProvider: String = "internal-demo",
        enabled: Boolean = true,
        providerIds: List<String> = listOf("internal-demo")
    ): PmsProviderProperties =
        PmsProviderProperties(
            activeProvider = activeProvider,
            providers = providerIds.associateWith {
                PmsConfiguredProviderProperties(enabled = enabled)
            }
        )

    private class StubPmsProvider(
        id: String,
        override val capabilities: PmsCapabilities = PmsCapabilities(
            roomListing = true,
            roomStatusLookup = true,
            roomStatusUpdate = true,
            stayLookup = true,
            assetLookup = true,
            issueTypeLookup = true,
            housekeepingStatusUpdate = true,
            maintenanceUpdate = true,
            eventCreation = true
        )
    ) : PmsProvider {
        override val id = PmsProviderId(id)
        override val displayName = "Stub PMS"

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
        ): PmsUpdateResult = PmsUpdateResult(verificationId, roomNumber, "ROOM_STATUS_UPDATE", request.status)

        override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult =
            PmsUpdateResult(verificationId, request.roomNumber, "MAINTENANCE_UPDATE", request.status)

        override fun createEvent(command: PmsEventCreateCommand): PmsEvent =
            PmsEvent(command.eventId ?: "event-1", command.type, command.subject)

        companion object {
            private val verificationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        }
    }
}
