package com.hotelopai.application.conversation

import com.hotelopai.assistant.application.AssistantConversationService
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.TaskConfirmationRepository
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssistantConversationServicePersistenceIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var assistantConversationService: AssistantConversationService

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var taskConfirmationRepository: TaskConfirmationRepository

    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Test
    fun `service persists conversation state and reuses confirmation idempotency record`() {
        val hotel = hotelRepository.save(
            Hotel(
                code = "assistant-service-${UuidV7Generator.generate()}",
                name = "Assistant Service Hotel"
            )
        )
        val conversation = assistantConversationService.startConversation(
            hotelId = hotel.id.toString(),
            userId = "user-1"
        ).conversation

        assistantConversationService.sendMessage(
            conversationId = conversation.id,
            text = "Room 101 AC not working",
            inputType = InputType.TEXT,
            attachments = emptyList()
        )

        val first = assistantConversationService.confirmTask(conversation.id, "confirm-service-1")
        val second = assistantConversationService.confirmTask(conversation.id, "confirm-service-1")

        assertThat(second.createdTaskId).isEqualTo(first.createdTaskId)
        assertThat(conversationRepository.findById(conversation.id)?.state)
            .isEqualTo(ConversationState.TASK_CREATED)
        assertThat(
            taskConfirmationRepository.findByConversationIdAndIdempotencyKey(
                conversation.id,
                "confirm-service-1"
            )?.createdTaskId
        ).isEqualTo(first.createdTaskId)
    }
}
