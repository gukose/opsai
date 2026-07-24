package com.hotelopai.integration.unimock

import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsProviderReadiness
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsProviderResourceNotFoundException
import com.hotelopai.pms.domain.PmsProviderTimeoutException
import com.hotelopai.pms.domain.PmsProviderUnavailableException
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import com.hotelopai.integration.unimock.InternalDemoPmsMapper.toDomain
import com.hotelopai.integration.unimock.InternalDemoPmsMapper.toInternalDemoRequest
import org.springframework.stereotype.Component

@Component
class InternalDemoPmsProvider(
    private val uniMockClient: UniMockClient
) : PmsProvider {
    override val id: PmsProviderId = ID
    override val displayName: String = "Internal Demo PMS"
    override val capabilities: PmsCapabilities = CAPABILITIES

    override fun readiness(config: PmsConfiguredProviderProperties?): PmsProviderReadiness {
        val base = super.readiness(config)
        if (!base.configured) {
            return base
        }
        return PmsProviderReadiness(
            configured = config?.authenticationConfig()?.mode == com.hotelopai.pms.application.PmsAuthenticationMode.NONE,
            enabled = base.enabled,
            message = if (config?.authenticationConfig()?.mode == com.hotelopai.pms.application.PmsAuthenticationMode.NONE) {
                null
            } else {
                "Internal demo PMS must use authentication mode NONE."
            }
        )
    }

    override fun listRooms(): List<PmsRoom> = withProviderHandling {
        uniMockClient.listRooms().map { it.toDomain() }
    }

    override fun findRoom(roomNumber: String): PmsRoom? = withProviderHandling {
        uniMockClient.getRoom(roomNumber)?.toDomain()
    }

    override fun findRoomStatus(roomNumber: String): PmsRoomStatus? = withProviderHandling {
        uniMockClient.getRoomStatus(roomNumber)?.toDomain()
    }

    override fun findStay(roomNumber: String): PmsStay? = withProviderHandling {
        uniMockClient.getRoomOccupancy(roomNumber)?.toDomain()
    }

    override fun getRoomAssets(roomNumber: String): List<PmsAsset> = withProviderHandling {
        uniMockClient.getRoomAssets(roomNumber).map { it.toDomain() }
    }

    override fun findAsset(assetId: String): PmsAsset? = withProviderHandling {
        uniMockClient.getAsset(assetId)?.toDomain()
    }

    override fun listIssueTypes(): List<PmsIssueType> = withProviderHandling {
        uniMockClient.listIssueTypes().map { it.toDomain() }
    }

    override fun findHousekeepingTask(taskId: String): PmsHousekeepingTask? = withProviderHandling {
        uniMockClient.getGuestRequest(taskId)?.toDomain()
    }

    override fun updateHousekeepingTaskStatus(
        taskId: String,
        request: HousekeepingTaskStatusUpdate
    ): PmsHousekeepingTask = withProviderHandling {
        uniMockClient.updateGuestRequestStatus(taskId, request.toInternalDemoRequest()).toDomain()
    }

    override fun updateRoomStatus(
        roomNumber: String,
        request: RoomStatusUpdate
    ): PmsUpdateResult = withProviderHandling {
        requireRoom(roomNumber)
        uniMockClient.updateRoomStatus(roomNumber, request.toInternalDemoRequest()).toDomain()
    }

    override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult = withProviderHandling {
        requireRoom(request.roomNumber)
        requireIssueType(request.issueTypeCode)
        uniMockClient.updateMaintenance(request.toInternalDemoRequest()).toDomain()
    }

    override fun createEvent(command: PmsEventCreateCommand): PmsEvent = withProviderHandling {
        uniMockClient.createEvent(command.toInternalDemoRequest()).toDomain()
    }

    private fun requireRoom(roomNumber: String) {
        if (uniMockClient.getRoom(roomNumber) == null) {
            throw PmsProviderResourceNotFoundException("Room", roomNumber)
        }
    }

    private fun requireIssueType(issueTypeCode: String) {
        if (uniMockClient.listIssueTypes().none { it.code == issueTypeCode }) {
            throw PmsProviderResourceNotFoundException("Issue type", issueTypeCode)
        }
    }

    private inline fun <T> withProviderHandling(operation: () -> T): T {
        try {
            return operation()
        } catch (exception: UniMockClientTimeoutException) {
            throw PmsProviderTimeoutException(exception.message ?: "Internal demo PMS timed out", exception)
        } catch (exception: UniMockClientUnavailableException) {
            throw PmsProviderUnavailableException(exception.message ?: "Internal demo PMS unavailable", exception)
        } catch (exception: UniMockClientRateLimitedException) {
            throw PmsProviderUnavailableException(exception.message ?: "Internal demo PMS rate limited", exception)
        } catch (exception: UniMockClientNotFoundException) {
            throw PmsProviderResourceNotFoundException(exception.message ?: "PMS resource not found")
        }
    }

    companion object {
        val ID = PmsProviderId("internal-demo")
        val CAPABILITIES = PmsCapabilities(
            hotelLookup = false,
            roomListing = true,
            roomStatusLookup = true,
            roomStatusUpdate = true,
            stayLookup = true,
            reservationLookup = false,
            guestLookup = false,
            assetLookup = true,
            issueTypeLookup = true,
            housekeepingStatusUpdate = true,
            maintenanceUpdate = true,
            eventRetrieval = false,
            eventCreation = true,
            webhooks = false,
            incrementalSync = false
        )
    }
}
