package com.hotelopai.assistant

import com.hotelopai.assistant.application.FieldKeys
import com.hotelopai.assistant.application.MaintenanceIntentDefinition
import com.hotelopai.assistant.application.OperationalIntent
import com.hotelopai.assistant.application.OperationalIntentService
import com.hotelopai.assistant.domain.Conversation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OperationalIntentServiceTest {
    private val service = OperationalIntentService()

    @Test
    fun `extracts multilingual HVAC intent and room`() {
        val result = service.interpret("Oda 502 klima bozuk", "tr")
        assertThat(result.intent).isEqualTo(OperationalIntent.MAINTENANCE_REQUEST)
        assertThat(result.entities.location).isEqualTo("Room 502")
        assertThat(result.entities.requiredSkill).isEqualTo("HVAC")
        assertThat(result.confirmationRequired).isFalse()
    }

    @Test
    fun `extracts Turkish spoken room number before room noun`() {
        val text = "Yüz bir numaralı odada klima çalışmıyor."
        val result = service.interpret(text, "tr")
        val fields = MaintenanceIntentDefinition().extractFields(
            Conversation(id = "conversation", hotelId = "hotel", userId = "user"),
            text
        )

        assertThat(result.intent).isEqualTo(OperationalIntent.MAINTENANCE_REQUEST)
        assertThat(result.entities.location).isEqualTo("Room 101")
        assertThat(result.confirmationRequired).isFalse()
        assertThat(fields[FieldKeys.ROOM_NUMBER]).isEqualTo("101")
    }

    @Test
    fun `low confidence unknown requires confirmation`() {
        val result = service.interpret("help")
        assertThat(result.intent).isEqualTo(OperationalIntent.UNKNOWN)
        assertThat(result.confirmationRequired).isTrue()
    }
}
