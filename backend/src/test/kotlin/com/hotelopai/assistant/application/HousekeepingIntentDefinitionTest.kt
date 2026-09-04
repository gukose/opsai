package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.IntentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HousekeepingIntentDefinitionTest {
    @Test
    fun `dirty Turkish room report becomes canonical housekeeping preview`() {
        val definition = HousekeepingIntentDefinition()
        val conversation = Conversation(id = "conversation", hotelId = "hotel", userId = "user")
        val text = "109 numaralı oda çok pis"

        assertThat(definition.matchScore(conversation, text)).isGreaterThan(0.65)
        val fields = definition.extractFields(conversation, text)
        val preview = definition.buildPreview(fields)

        assertThat(preview.type).isEqualTo(IntentType.HOUSEKEEPING)
        assertThat(preview.roomNumber).isEqualTo("109")
        assertThat(preview.assignedTeam).isEqualTo("Housekeeping")
    }
}
