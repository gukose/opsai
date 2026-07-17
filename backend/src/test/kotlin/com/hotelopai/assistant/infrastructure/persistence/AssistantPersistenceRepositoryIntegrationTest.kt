package com.hotelopai.assistant.infrastructure.persistence

import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.ConversationConcurrencyException
import com.hotelopai.assistant.application.TaskConfirmationRecord
import com.hotelopai.assistant.application.TaskConfirmationRepository
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.FollowUpOption
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.ImageObservation
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.MessageRole
import com.hotelopai.assistant.domain.MissingField
import com.hotelopai.assistant.domain.TaskPreview
import com.hotelopai.assistant.domain.VoiceTranscriptMetadata
import com.hotelopai.assistant.domain.VoiceTranscriptSource
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssistantPersistenceRepositoryIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var taskConfirmationRepository: TaskConfirmationRepository

    @Test
    fun `conversation repository saves and loads full aggregate state`() {
        val conversation = Conversation(
            id = "conversation-roundtrip-1",
            hotelId = "hotel-opai-demo",
            userId = "user-1",
            state = ConversationState.WAITING_FOR_CONFIRMATION,
            messages = listOf(
                ConversationMessage(
                    id = "message-1",
                    role = MessageRole.USER,
                    inputType = InputType.VOICE,
                    text = "Room 502 sink is leaking",
                    voiceTranscript = "Room 502 sink is leaking",
                    voiceTranscriptMetadata = VoiceTranscriptMetadata(
                        transcript = "Room 502 sink is leaking",
                        languageCode = "en",
                        durationMs = 1200,
                        source = VoiceTranscriptSource.CLIENT_TRANSCRIPT
                    ),
                    audioMetadata = AudioMetadata(
                        originalFileName = "request.m4a",
                        mimeType = "audio/mp4",
                        durationMs = 1200,
                        sizeBytes = 4567
                    ),
                    attachments = listOf(
                        ConversationAttachment(
                            id = "attachment-1",
                            type = AttachmentType.IMAGE,
                            originalFileName = "sink.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 1024,
                            widthPx = 800,
                            heightPx = 600,
                            localReference = "local://sink.jpg",
                            storageStatus = AttachmentStorageStatus.LOCAL_METADATA_ONLY
                        )
                    ),
                    imageObservations = listOf(
                        ImageObservation(
                            id = "observation-1",
                            attachmentId = "attachment-1",
                            text = "Water visible under the sink"
                        )
                    ),
                    createdAt = Instant.parse("2026-07-10T10:00:00Z")
                )
            ),
            intent = IntentType.MAINTENANCE,
            collectedFields = mapOf(
                "roomNumber" to "502",
                "description" to "Sink is leaking"
            ),
            missingFields = listOf(
                MissingField(
                    key = "priority",
                    label = "Priority",
                    required = false
                )
            ),
            followUpQuestion = FollowUpQuestion(
                id = "follow-up-1",
                fieldKey = "priority",
                prompt = "What priority should this be?",
                options = listOf(
                    FollowUpOption("priority-medium", "Medium", "Medium"),
                    FollowUpOption("priority-high", "High", "High")
                )
            ),
            taskPreview = taskPreview(),
            activeDraftId = "draft-1",
            draftVersion = 2,
            createdTaskId = "018f6b7a-4f22-7caa-8f60-9e4b0f7f1111",
            confirmationIdempotencyKey = "confirm-1",
            createdAt = Instant.parse("2026-07-10T09:59:00Z"),
            updatedAt = Instant.parse("2026-07-10T10:01:00Z")
        )

        val saved = conversationRepository.save(conversation)

        assertThat(saved.rowVersion).isEqualTo(0)
        assertThat(conversationRepository.findById(conversation.id)).isEqualTo(conversation)
    }

    @Test
    fun `conversation repository updates existing conversation`() {
        val initial = Conversation(
            id = "conversation-update-1",
            hotelId = "hotel-opai-demo",
            userId = "user-1",
            createdAt = Instant.parse("2026-07-10T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-10T10:00:00Z")
        )
        val updated = initial.copy(
            state = ConversationState.WAITING_FOR_USER_ANSWER,
            intent = IntentType.GUEST_REQUEST,
            collectedFields = mapOf("description" to "Guest needs towels"),
            missingFields = listOf(MissingField("roomNumber", "Room")),
            followUpQuestion = FollowUpQuestion(
                id = "room-question",
                fieldKey = "roomNumber",
                prompt = "Which room is this request for?"
            ),
            updatedAt = Instant.parse("2026-07-10T10:05:00Z")
        )

        val savedInitial = conversationRepository.save(initial)
        val savedUpdated = conversationRepository.save(updated.copy(rowVersion = savedInitial.rowVersion))

        assertThat(savedUpdated.rowVersion).isEqualTo(1)
        assertThat(conversationRepository.findById(initial.id)).isEqualTo(savedUpdated)
    }

    @Test
    fun `stale conversation save is rejected and successful save increments version`() {
        val initial = conversationRepository.save(
            Conversation(
                id = "conversation-stale-1",
                hotelId = "hotel-opai-demo",
                userId = "user-1",
                createdAt = Instant.parse("2026-07-10T10:00:00Z"),
                updatedAt = Instant.parse("2026-07-10T10:00:00Z")
            )
        )
        val firstUpdate = conversationRepository.save(
            initial.copy(
                state = ConversationState.WAITING_FOR_USER_ANSWER,
                updatedAt = Instant.parse("2026-07-10T10:01:00Z")
            )
        )

        assertThat(firstUpdate.rowVersion).isEqualTo(1)
        assertThrows(ConversationConcurrencyException::class.java) {
            conversationRepository.save(
                initial.copy(
                    state = ConversationState.RESET,
                    updatedAt = Instant.parse("2026-07-10T10:02:00Z")
                )
            )
        }
        assertThat(conversationRepository.findById(initial.id)).isEqualTo(firstUpdate)
    }

    @Test
    fun `task confirmation repository saves and loads idempotency record`() {
        val conversation = Conversation(
            id = "conversation-confirmation-1",
            hotelId = "hotel-opai-demo",
            userId = "user-1",
            createdAt = Instant.parse("2026-07-10T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-10T10:00:00Z")
        )
        val record = TaskConfirmationRecord(
            conversationId = conversation.id,
            idempotencyKey = "confirm-502",
            createdTaskId = "018f6b7a-4f22-7caa-8f60-9e4b0f7f2222",
            draftId = "draft-502",
            draftVersion = 3,
            preview = taskPreview(),
            createdAt = Instant.parse("2026-07-10T10:02:00Z")
        )

        conversationRepository.save(conversation)
        taskConfirmationRepository.save(record)

        assertThat(
            taskConfirmationRepository.findByConversationIdAndIdempotencyKey(
                conversation.id,
                "confirm-502"
            )
        ).isEqualTo(record)
        assertThat(
            taskConfirmationRepository.findByConversationIdAndDraftIdentity(
                conversation.id,
                "draft-502",
                3
            )
        ).isEqualTo(record)
    }

    private fun taskPreview(): TaskPreview =
        TaskPreview(
            type = IntentType.MAINTENANCE,
            title = "Fix leaking sink",
            description = "Room 502 sink is leaking",
            roomNumber = "502",
            publicAreaId = null,
            assetId = "sink-502",
            assignedTeam = "Maintenance",
            priority = "High",
            slaMinutes = 45,
            requiresPmsUpdate = true
        )
}
