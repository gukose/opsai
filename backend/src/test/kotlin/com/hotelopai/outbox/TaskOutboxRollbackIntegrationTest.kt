package com.hotelopai.outbox

import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class TaskOutboxRollbackIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskLifecycleService: TaskLifecycleService

    @Test
    fun `outbox enqueue failure rolls back task creation`() {
        val hotel = hotelRepository.save(
            Hotel(
                code = "outbox-rollback-${UuidV7Generator.generate()}",
                name = "Outbox Rollback Hotel"
            )
        )

        assertThatThrownBy {
            taskLifecycleService.createTask(
                CreateTaskCommand(
                    hotelId = hotel.id,
                    intentType = TaskIntentType.MAINTENANCE,
                    source = TaskSource.MANUAL,
                    title = "Rollback task",
                    description = "Rollback task description",
                    priority = TaskPriority.HIGH,
                    slaDeadline = Instant.now().plusSeconds(3600)
                )
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("forced_outbox_failure")

        assertThat(taskRepository.findAllByHotelId(hotel.id)).isEmpty()
    }

    @TestConfiguration
    class FailingOutboxConfiguration {
        @Bean
        @Primary
        fun failingOutboxRepository(): OperationalOutboxRepository =
            object : OperationalOutboxRepository {
                override fun save(event: OperationalOutboxEvent): OperationalOutboxEvent =
                    throw RuntimeException("forced_outbox_failure")

                override fun findById(id: UUID): OperationalOutboxEvent? = null

                override fun findByEventAggregate(
                    eventType: String,
                    aggregateType: String,
                    aggregateId: UUID
                ): OperationalOutboxEvent? = null

                override fun claimDue(
                    now: Instant,
                    batchSize: Int,
                    processorId: String
                ): List<OperationalOutboxEvent> = emptyList()

                override fun markCompleted(id: UUID, now: Instant) = Unit

                override fun markRetryable(
                    id: UUID,
                    attemptCount: Int,
                    nextAttemptAt: Instant,
                    failureCode: String,
                    failureMessage: String?,
                    now: Instant
                ) = Unit

                override fun markFailed(
                    id: UUID,
                    attemptCount: Int,
                    failureCode: String,
                    failureMessage: String?,
                    now: Instant
                ) = Unit

                override fun recoverStale(cutoff: Instant, now: Instant): Int = 0
            }
    }
}
