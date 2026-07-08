package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "task_log")
class TaskLogJpaEntity : AuditedJpaEntity() {
    @Column(name = "task_id", nullable = false, columnDefinition = "uuid")
    var taskId: UUID? = null

    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    var operation: TaskTransition = TaskTransition.CREATE

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    var outcome: TaskLogOutcomeJpa = TaskLogOutcomeJpa.SUCCESS

    @Column(name = "message", nullable = false)
    var message: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    var fromStatus: TaskStatus? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    var toStatus: TaskStatus? = null

    @Column(name = "correlation_id")
    var correlationId: String? = null
}

enum class TaskLogOutcomeJpa {
    SUCCESS,
    FAILED
}
