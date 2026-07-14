package com.hotelopai.dashboard.application

import com.hotelopai.dashboard.domain.DashboardTimeRange
import com.hotelopai.notification.domain.NotificationType
import java.time.Instant
import java.util.UUID

data class DashboardSummary(
    val hotelId: UUID,
    val range: DashboardTimeRange,
    val generatedAt: Instant,
    val tasks: DashboardTaskSummary,
    val sla: DashboardSlaSummary,
    val notifications: DashboardNotificationSummary,
    val workload: DashboardWorkloadSummary
)

data class DashboardTaskSummary(
    val createdInRange: DashboardTaskCreatedInRangeSummary,
    val currentSnapshot: DashboardTaskCurrentSnapshotSummary
)

data class DashboardTaskCreatedInRangeSummary(
    val total: Long,
    val statusCounts: DashboardTaskStatusCounts
)

data class DashboardTaskCurrentSnapshotSummary(
    val active: Long,
    val urgent: Long,
    val unassigned: Long,
    val completionPercent: Int,
    val statusCounts: DashboardTaskStatusCounts
)

data class DashboardTaskStatusCounts(
    val created: Long = 0,
    val assigned: Long = 0,
    val started: Long = 0,
    val inProgress: Long = 0,
    val waiting: Long = 0,
    val overdue: Long = 0,
    val completed: Long = 0,
    val cancelled: Long = 0
)

data class DashboardSlaSummary(
    val dueSoon: Long,
    val overdue: Long,
    val breached: Long
)

data class DashboardNotificationSummary(
    val unread: Long,
    val recent: List<DashboardRecentNotification>
)

data class DashboardRecentNotification(
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val sourceTaskId: UUID?
)

data class DashboardWorkloadSummary(
    val assignedToUser: Long,
    val assignedToRole: Long,
    val unassigned: Long
)
