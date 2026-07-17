package com.hotelopai.application.task

import com.hotelopai.hotel.application.HotelRepository
import com.hotelopai.hotel.domain.Hotel
import com.hotelopai.notification.application.NotificationRepository
import com.hotelopai.notification.domain.NotificationRecipient
import com.hotelopai.outbox.application.OperationalOutboxProcessor
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.outbox.domain.OperationalOutboxStatus
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.support.PostgresIntegrationTestSupport
import com.hotelopai.task.application.AssignmentCommand
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class TaskNotificationIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var hotelRepository: HotelRepository

    @Autowired
    private lateinit var taskLifecycleService: TaskLifecycleService

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var outboxRepository: OperationalOutboxRepository

    @Autowired
    private lateinit var outboxProcessor: OperationalOutboxProcessor

    @Test
    fun `creating a task enqueues one event and processor creates exactly one notification`() {
        val hotel = hotelRepository.save(
            Hotel(code = "task-notification-${UuidV7Generator.generate()}", name = "Task Notification Hotel")
        )
        val created = taskLifecycleService.createTask(
            CreateTaskCommand(
                hotelId = hotel.id,
                intentType = TaskIntentType.MAINTENANCE,
                source = TaskSource.MANUAL,
                title = "AC not working",
                description = "Room 101 AC not working",
                priority = TaskPriority.HIGH,
                slaDeadline = Instant.now().plusSeconds(3600)
            )
        )

        assertThat(notificationRepository.countBySourceTaskId(created.id)).isEqualTo(0)
        val event = outboxRepository.findByEventAggregate(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = created.id
        ) ?: error("task-created outbox event missing")
        assertThat(event.status).isEqualTo(OperationalOutboxStatus.PENDING)

        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        assertThat(notificationRepository.countBySourceTaskId(created.id)).isEqualTo(1)
        assertThat(notificationRepository.countBySourceEventId(event.id)).isEqualTo(1)
        assertThat(outboxRepository.findById(event.id)?.status).isEqualTo(OperationalOutboxStatus.COMPLETED)

        outboxProcessor.processBatch()
        assertThat(notificationRepository.countBySourceTaskId(created.id)).isEqualTo(1)
    }

    @Test
    fun `task created notification targets assigned user when task is created with user assignment`() {
        val hotel = hotelRepository.save(
            Hotel(code = "task-user-notification-${UuidV7Generator.generate()}", name = "Task User Notification Hotel")
        )
        val assignedUserId = UuidV7Generator.generate()
        val created = taskLifecycleService.createTask(
            CreateTaskCommand(
                hotelId = hotel.id,
                intentType = TaskIntentType.GUEST_REQUEST,
                source = TaskSource.MANUAL,
                title = "Extra towels",
                description = "Guest needs extra towels",
                priority = TaskPriority.MEDIUM,
                slaDeadline = Instant.now().plusSeconds(3600),
                assignment = AssignmentCommand(
                    assigneeType = TaskAssigneeType.USER,
                    assigneeId = assignedUserId.toString(),
                    displayName = "Assigned User"
                )
            )
        )

        val event = outboxRepository.findByEventAggregate(
            eventType = OperationalOutboxEventTypes.TASK_CREATED,
            aggregateType = OperationalOutboxAggregateTypes.TASK,
            aggregateId = created.id
        ) ?: error("task-created outbox event missing")
        assertThat(notificationRepository.countBySourceTaskId(created.id)).isEqualTo(0)
        assertThat(outboxProcessor.processBatch()).isGreaterThanOrEqualTo(1)

        val accessible = notificationRepository.findAccessible(
            hotelId = hotel.id,
            userId = assignedUserId,
            roleCodes = emptySet()
        )

        assertThat(notificationRepository.countBySourceTaskId(created.id)).isEqualTo(1)
        assertThat(accessible).hasSize(1)
        val notification = accessible.single()
        assertThat(notification.sourceTaskId).isEqualTo(created.id)
        assertThat(notification.sourceEventId).isEqualTo(event.id)
        assertThat(notification.recipient).isEqualTo(NotificationRecipient.User(assignedUserId))
    }
}
