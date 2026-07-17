package com.hotelopai.assistant.application

import com.hotelopai.task.application.TaskPage
import com.hotelopai.task.application.TaskPageRequest
import com.hotelopai.observability.OperationalObservability
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.infrastructure.InMemoryConversationRepository
import com.hotelopai.assistant.infrastructure.InMemoryTaskConfirmationRepository
import com.hotelopai.task.infrastructure.InMemoryTaskRepository
import com.hotelopai.shared.kernel.UuidV7Generator
import java.util.UUID

class AssistantConversationServiceTest {
    @Test
    fun `confirm persists task and returns created task id`() {
        val fixtures = createFixtures()
        val conversation = fixtures.service.startConversation(UuidV7Generator.generate().toString(), "user-1").conversation

        fixtures.service.sendMessage(
            conversationId = conversation.id,
            text = "Room 101 AC not working",
            inputType = InputType.TEXT,
            attachments = emptyList()
        )

        val result = fixtures.service.confirmTask(conversation.id, "confirm-1")

        assertEquals(ConversationState.TASK_CREATED, result.conversation.state)
        assertNotNull(result.createdTaskId)
        assertEquals(result.createdTaskId, result.conversation.createdTaskId)
        assertEquals(1, fixtures.taskRepository.findAll().size)
        val taskId = UUID.fromString(result.createdTaskId!!)
        assertEquals(taskId, fixtures.taskRepository.findById(taskId)?.id)
    }

    @Test
    fun `confirm with same idempotency key does not create duplicate tasks`() {
        val fixtures = createFixtures()
        val conversation = fixtures.service.startConversation(UuidV7Generator.generate().toString(), "user-1").conversation

        fixtures.service.sendMessage(
            conversationId = conversation.id,
            text = "Room 101 AC not working",
            inputType = InputType.TEXT,
            attachments = emptyList()
        )

        val first = fixtures.service.confirmTask(conversation.id, "confirm-1")
        val second = fixtures.service.confirmTask(conversation.id, "confirm-1")

        assertEquals(first.createdTaskId, second.createdTaskId)
        assertEquals(1, fixtures.taskRepository.findAll().size)
    }

    @Test
    fun `confirm with different idempotency key for same draft returns existing task`() {
        val fixtures = createFixtures()
        val conversation = fixtures.service.startConversation(UuidV7Generator.generate().toString(), "user-1").conversation

        fixtures.service.sendMessage(
            conversationId = conversation.id,
            text = "Room 101 AC not working",
            inputType = InputType.TEXT,
            attachments = emptyList()
        )

        val first = fixtures.service.confirmTask(conversation.id, "confirm-1")
        val second = fixtures.service.confirmTask(conversation.id, "confirm-2")

        assertEquals(first.createdTaskId, second.createdTaskId)
        assertEquals(1, fixtures.taskRepository.findAll().size)
    }

    @Test
    fun `assistant message and confirmation outcomes record observability metrics`() {
        val fixtures = createFixtures()
        val conversation = fixtures.service.startConversation(UuidV7Generator.generate().toString(), "user-1").conversation

        fixtures.service.sendMessage(
            conversationId = conversation.id,
            text = "Room 101 AC not working",
            inputType = InputType.TEXT,
            attachments = emptyList()
        )

        fixtures.service.confirmTask(conversation.id, "confirm-1")
        fixtures.service.confirmTask(conversation.id, "confirm-1")

        assertEquals(
            1.0,
            fixtures.counter(
                "hotelopai.assistant.message.total",
                "operation" to "send",
                "outcome" to "preview"
            )
        )
        assertEquals(
            1.0,
            fixtures.counter(
                "hotelopai.assistant.confirmation.total",
                "operation" to "confirm",
                "outcome" to "success"
            )
        )
        assertEquals(
            1.0,
            fixtures.counter(
                "hotelopai.assistant.confirmation.total",
                "operation" to "confirm",
                "outcome" to "duplicate",
                "reason_code" to "idempotency_reuse"
            )
        )
        assertEquals(1, fixtures.timerCount("hotelopai.assistant.interpretation.duration"))
        assertEquals(2, fixtures.timerCount("hotelopai.assistant.confirmation.duration"))
    }

    @Test
    fun `confirm before preview is rejected`() {
        val fixtures = createFixtures()
        val conversation = fixtures.service.startConversation(UuidV7Generator.generate().toString(), "user-1").conversation

        assertThrows(IllegalArgumentException::class.java) {
            fixtures.service.confirmTask(conversation.id, "confirm-1")
        }
    }

    @Test
    fun `task persistence failure does not move conversation to task created`() {
        val taskRepository = FailingTaskRepository()
        val fixtures = createFixtures(taskRepository)
        val conversation = fixtures.service.startConversation(UuidV7Generator.generate().toString(), "user-1").conversation

        fixtures.service.sendMessage(
            conversationId = conversation.id,
            text = "Room 101 AC not working",
            inputType = InputType.TEXT,
            attachments = emptyList()
        )

        assertEquals(ConversationState.WAITING_FOR_CONFIRMATION, fixtures.conversationRepository.findById(conversation.id)?.state)

        assertThrows(RuntimeException::class.java) {
            fixtures.service.confirmTask(conversation.id, "confirm-1")
        }

        assertEquals(ConversationState.WAITING_FOR_CONFIRMATION, fixtures.conversationRepository.findById(conversation.id)?.state)
        assertEquals(0, taskRepository.findAll().size)
    }

    private fun createFixtures(
        taskRepository: com.hotelopai.task.application.TaskRepository = InMemoryTaskRepository()
    ): Fixtures {
        val conversationRepository = InMemoryConversationRepository()
        val taskConfirmationRepository = InMemoryTaskConfirmationRepository()
        val meterRegistry = SimpleMeterRegistry()
        val observability = OperationalObservability(meterRegistry)
        val taskApplicationPort = TaskLifecycleService(taskRepository)
        val service = AssistantConversationService(
            conversationRepository = conversationRepository,
            stateMachine = ConversationStateMachine(),
            taskApplicationPort = taskApplicationPort,
            taskConfirmationRepository = taskConfirmationRepository,
            observability = observability
        )

        return Fixtures(
            service = service,
            conversationRepository = conversationRepository,
            taskRepository = taskRepository,
            taskConfirmationRepository = taskConfirmationRepository,
            meterRegistry = meterRegistry
        )
    }

    private data class Fixtures(
        val service: AssistantConversationService,
        val conversationRepository: InMemoryConversationRepository,
        val taskRepository: com.hotelopai.task.application.TaskRepository,
        val taskConfirmationRepository: InMemoryTaskConfirmationRepository,
        val meterRegistry: SimpleMeterRegistry
    ) {
        fun counter(name: String, vararg tags: Pair<String, String>): Double =
            meterRegistry.find(name)
                .tags(*tags.flatMap { listOf(it.first, it.second) }.toTypedArray())
                .counter()
                ?.count()
                ?: 0.0

        fun timerCount(name: String): Long =
            meterRegistry.meters
                .filter { it.id.name == name }
                .sumOf { (it as? io.micrometer.core.instrument.Timer)?.count() ?: 0 }
    }

    private class FailingTaskRepository : com.hotelopai.task.application.TaskRepository {
        override fun save(task: com.hotelopai.task.domain.Task): com.hotelopai.task.domain.Task {
            throw RuntimeException("Task persistence failed")
        }

        override fun findById(id: UUID): com.hotelopai.task.domain.Task? = null

        override fun findAll(): List<com.hotelopai.task.domain.Task> = emptyList()

        override fun findPage(request: TaskPageRequest): TaskPage<com.hotelopai.task.domain.Task> =
            TaskPage(emptyList(), request.page, request.size, 0)
    }
}
