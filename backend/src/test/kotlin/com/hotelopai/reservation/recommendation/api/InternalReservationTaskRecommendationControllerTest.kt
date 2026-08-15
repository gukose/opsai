package com.hotelopai.reservation.recommendation.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InternalReservationTaskRecommendationControllerTest {
    @Test
    fun `pilot decision CSV cells are deterministic and formula safe`() {
        assertThat(safePilotDecisionCsvCell("normal")).isEqualTo("\"normal\"")
        assertThat(safePilotDecisionCsvCell("=cmd")).isEqualTo("\"'=cmd\"")
        assertThat(safePilotDecisionCsvCell("+cmd")).isEqualTo("\"'+cmd\"")
        assertThat(safePilotDecisionCsvCell("-cmd")).isEqualTo("\"'-cmd\"")
        assertThat(safePilotDecisionCsvCell("@cmd")).isEqualTo("\"'@cmd\"")
        assertThat(safePilotDecisionCsvCell("quoted \"value\"")).isEqualTo("\"quoted \"\"value\"\"\"")
    }
}
