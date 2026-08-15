package com.hotelopai.housekeeping

import com.hotelopai.housekeeping.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class HousekeepingWorkflowTest {
    private val start=Instant.parse("2026-08-15T08:00:00Z")
    private fun workflow(inspection:Boolean=true)=HousekeepingWorkflow(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),HousekeepingWorkflowType.DEPARTURE_CLEANING,"101",HousekeepingStatus.CREATED,inspection,idempotencyKey="checkout-1",createdAt=start,updatedAt=start)

    @Test fun `working duration excludes paused duration and inspection can pass`() {
        val completed=workflow().assign(start).accept(start.plusSeconds(60)).start(start.plusSeconds(120))
            .pause(start.plusSeconds(720)).resume(start.plusSeconds(1020)).finishCleaning(start.plusSeconds(1620))
            .inspect(InspectionResult.PASS,start.plusSeconds(1680)).close(start.plusSeconds(1700))
        assertThat(completed.status).isEqualTo(HousekeepingStatus.CLOSED)
        assertThat(completed.workingSeconds).isEqualTo(1200)
        assertThat(completed.pausedSeconds).isEqualTo(300)
    }

    @Test fun `rejected inspection enters rework and can be assigned again`() {
        val rejected=workflow().assign(start).accept(start).start(start).finishCleaning(start.plusSeconds(60)).inspect(InspectionResult.REJECT,start.plusSeconds(70))
        assertThat(rejected.status).isEqualTo(HousekeepingStatus.REWORK)
        assertThat(rejected.assign(start.plusSeconds(80)).status).isEqualTo(HousekeepingStatus.ASSIGNED)
    }

    @Test fun `inspection rejection requires reason in inspection record`() {
        assertThatThrownBy { HousekeepingInspection(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,InspectionResult.REJECT,null,null,emptyList(),start,start) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
