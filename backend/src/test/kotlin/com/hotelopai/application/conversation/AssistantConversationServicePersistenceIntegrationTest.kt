package com.hotelopai.application.conversation

import com.hotelopai.assistant.application.AssistantConversationService
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.TaskConfirmationRepository
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
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

    @Autowired
    private lateinit var outboxRepository: OperationalOutboxRepository

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
        val differentKey = assistantConversationService.confirmTask(conversation.id, "confirm-service-2")

        assertThat(second.createdTaskId).isEqualTo(first.createdTaskId)
        assertThat(differentKey.createdTaskId).isEqualTo(first.createdTaskId)
        assertThat(conversationRepository.findById(conversation.id)?.state)
            .isEqualTo(ConversationState.TASK_CREATED)
        assertThat(
            taskConfirmationRepository.findByConversationIdAndIdempotencyKey(
                conversation.id,
                "confirm-service-1"
            )?.createdTaskId
        ).isEqualTo(first.createdTaskId)
        val taskId = java.util.UUID.fromString(requireNotNull(first.createdTaskId))
        assertThat(
            outboxRepository.findByEventAggregate(
                eventType = OperationalOutboxEventTypes.TASK_CREATED,
                aggregateType = OperationalOutboxAggregateTypes.TASK,
                aggregateId = taskId
            )
        ).isNotNull
    }

    @Test
    fun `new draft version is independently confirmable`() {
        val hotel = hotelRepository.save(
            Hotel(
                code = "assistant-service-new-draft-${UuidV7Generator.generate()}",
                name = "Assistant Service New Draft Hotel"
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
        val first = assistantConversationService.confirmTask(conversation.id, "confirm-draft-1")

        assistantConversationService.sendMessage(
            conversationId = conversation.id,
            text = "Room 102 sink is leaking",
            inputType = InputType.TEXT,
            attachments = emptyList()
        )
        val second = assistantConversationService.confirmTask(conversation.id, "confirm-draft-2")

        assertThat(second.createdTaskId).isNotEqualTo(first.createdTaskId)
        assertThat(conversationRepository.findById(conversation.id)?.draftVersion).isEqualTo(2)
        listOf(first.createdTaskId, second.createdTaskId).forEach { createdTaskId ->
            assertThat(
                outboxRepository.findByEventAggregate(
                    eventType = OperationalOutboxEventTypes.TASK_CREATED,
                    aggregateType = OperationalOutboxAggregateTypes.TASK,
                    aggregateId = java.util.UUID.fromString(requireNotNull(createdTaskId))
                )
            ).isNotNull
        }
    }
}
