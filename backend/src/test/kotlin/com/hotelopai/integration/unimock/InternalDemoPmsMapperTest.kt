package com.hotelopai.integration.unimock

import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.integration.unimock.InternalDemoPmsMapper.toDomain
import com.hotelopai.integration.unimock.InternalDemoPmsMapper.toInternalDemoRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InternalDemoPmsMapperTest {
    @Test
    fun `room mapper separates internal demo DTO from domain room`() {
        val room = PmsRoom(
            roomId = "room-101",
            roomNumber = "101",
            roomTypeId = "type-deluxe",
            roomTypeName = "Deluxe",
            floor = "1",
            occupied = true,
            status = "OCCUPIED"
        ).toDomain()

        assertEquals("room-101", room.id)
        assertEquals("101", room.number)
        assertEquals("Deluxe", room.roomTypeName)
        assertEquals(true, room.occupied)
    }

    @Test
    fun `maintenance mapper creates internal demo request without leaking vendor DTO upstream`() {
        val request: PmsMaintenanceUpdateRequest = MaintenanceUpdate(
            roomNumber = "101",
            issueTypeCode = "MAINTENANCE_AC",
            description = "AC resolved",
            status = "RESOLVED"
        ).toInternalDemoRequest()

        assertEquals("101", request.roomNumber)
        assertEquals("MAINTENANCE_AC", request.issueTypeCode)
        assertEquals("RESOLVED", request.status)
    }

    @Test
    fun `update result mapper preserves verification metadata`() {
        val verificationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val result = PmsUpdateResult(
            verificationLogId = verificationId,
            entityId = "101",
            operation = "ROOM_STATUS_UPDATE",
            status = "OUT_OF_ORDER"
        ).toDomain()

        assertEquals(verificationId, result.verificationLogId)
        assertEquals("101", result.entityId)
        assertEquals("ROOM_STATUS_UPDATE", result.operation)
    }
}
