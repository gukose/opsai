package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.infrastructure.persistence.AuditedJpaEntity
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "task")
class TaskJpaEntity : AuditedJpaEntity() {
    @Column(name = "id", nullable = false, updatable = false)
    override var id: java.util.UUID? = null

    @Column(name = "hotel_id", nullable = false, columnDefinition = "uuid")
    var hotelId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "intent_type", nullable = false)
    var intentType: TaskIntentType = TaskIntentType.GUEST_REQUEST

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    var source: TaskSource = TaskSource.MANUAL

    @Column(name = "title", nullable = false)
    var title: String = ""

    @Column(name = "description", nullable = false)
    var description: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    var priority: TaskPriority = TaskPriority.MEDIUM

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TaskStatus = TaskStatus.CREATED

    @Column(name = "sla_deadline", nullable = false)
    var slaDeadline: Instant? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type")
    var assigneeType: TaskAssigneeType? = null

    @Column(name = "assignee_id")
    var assigneeId: String? = null

    @Column(name = "assignee_display_name")
    var assigneeDisplayName: String? = null

    @Column(name = "assigned_at")
    var assignedAt: Instant? = null

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null

    @Column(name = "overdue_at")
    var overdueAt: Instant? = null
}
