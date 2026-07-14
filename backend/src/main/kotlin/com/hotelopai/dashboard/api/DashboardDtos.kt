package com.hotelopai.dashboard.api

import com.hotelopai.dashboard.application.DashboardNotificationSummary
import com.hotelopai.dashboard.application.DashboardRecentNotification
import com.hotelopai.dashboard.application.DashboardSlaSummary
import com.hotelopai.dashboard.application.DashboardSummary
import com.hotelopai.dashboard.application.DashboardTaskCreatedInRangeSummary
import com.hotelopai.dashboard.application.DashboardTaskCurrentSnapshotSummary
import com.hotelopai.dashboard.application.DashboardTaskStatusCounts
import com.hotelopai.dashboard.application.DashboardTaskSummary
import com.hotelopai.dashboard.application.DashboardWorkloadSummary
import java.time.Instant

data class DashboardSummaryResponse(
    val hotelId: String,
    val range: String,
    val generatedAt: Instant,
    val tasks: DashboardTaskSummaryResponse,
    val sla: DashboardSlaSummaryResponse,
    val notifications: DashboardNotificationSummaryResponse,
    val workload: DashboardWorkloadSummaryResponse
) {
    companion object {
        fun from(summary: DashboardSummary): DashboardSummaryResponse =
            DashboardSummaryResponse(
                hotelId = summary.hotelId.toString(),
                range = summary.range.wireValue,
                generatedAt = summary.generatedAt,
                tasks = DashboardTaskSummaryResponse.from(summary.tasks),
                sla = DashboardSlaSummaryResponse.from(summary.sla),
                notifications = DashboardNotificationSummaryResponse.from(summary.notifications),
                workload = DashboardWorkloadSummaryResponse.from(summary.workload)
            )
    }
}

data class DashboardTaskSummaryResponse(
    val createdInRange: DashboardTaskCreatedInRangeResponse,
    val currentSnapshot: DashboardTaskCurrentSnapshotResponse
) {
    companion object {
        fun from(summary: DashboardTaskSummary): DashboardTaskSummaryResponse =
            DashboardTaskSummaryResponse(
                createdInRange = DashboardTaskCreatedInRangeResponse.from(summary.createdInRange),
                currentSnapshot = DashboardTaskCurrentSnapshotResponse.from(summary.currentSnapshot)
            )
    }
}

data class DashboardTaskCreatedInRangeResponse(
    val total: Long,
    val statusCounts: DashboardTaskStatusCountsResponse
) {
    companion object {
        fun from(summary: DashboardTaskCreatedInRangeSummary): DashboardTaskCreatedInRangeResponse =
            DashboardTaskCreatedInRangeResponse(
                total = summary.total,
                statusCounts = DashboardTaskStatusCountsResponse.from(summary.statusCounts)
            )
    }
}

data class DashboardTaskCurrentSnapshotResponse(
    val active: Long,
    val urgent: Long,
    val unassigned: Long,
    val completionPercent: Int,
    val statusCounts: DashboardTaskStatusCountsResponse
) {
    companion object {
        fun from(summary: DashboardTaskCurrentSnapshotSummary): DashboardTaskCurrentSnapshotResponse =
            DashboardTaskCurrentSnapshotResponse(
                active = summary.active,
                urgent = summary.urgent,
                unassigned = summary.unassigned,
                completionPercent = summary.completionPercent,
                statusCounts = DashboardTaskStatusCountsResponse.from(summary.statusCounts)
            )
    }
}

data class DashboardTaskStatusCountsResponse(
    val created: Long,
    val assigned: Long,
    val started: Long,
    val inProgress: Long,
    val waiting: Long,
    val overdue: Long,
    val completed: Long,
    val cancelled: Long
) {
    companion object {
        fun from(counts: DashboardTaskStatusCounts): DashboardTaskStatusCountsResponse =
            DashboardTaskStatusCountsResponse(
                created = counts.created,
                assigned = counts.assigned,
                started = counts.started,
                inProgress = counts.inProgress,
                waiting = counts.waiting,
                overdue = counts.overdue,
                completed = counts.completed,
                cancelled = counts.cancelled
            )
    }
}

data class DashboardSlaSummaryResponse(
    val dueSoon: Long,
    val overdue: Long,
    val breached: Long
) {
    companion object {
        fun from(summary: DashboardSlaSummary): DashboardSlaSummaryResponse =
            DashboardSlaSummaryResponse(
                dueSoon = summary.dueSoon,
                overdue = summary.overdue,
                breached = summary.breached
            )
    }
}

data class DashboardNotificationSummaryResponse(
    val unread: Long,
    val recent: List<DashboardRecentNotificationResponse>
) {
    companion object {
        fun from(summary: DashboardNotificationSummary): DashboardNotificationSummaryResponse =
            DashboardNotificationSummaryResponse(
                unread = summary.unread,
                recent = summary.recent.map(DashboardRecentNotificationResponse::from)
            )
    }
}

data class DashboardRecentNotificationResponse(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val sourceTaskId: String?
) {
    companion object {
        fun from(notification: DashboardRecentNotification): DashboardRecentNotificationResponse =
            DashboardRecentNotificationResponse(
                id = notification.id.toString(),
                type = notification.type.name,
                title = notification.title,
                body = notification.body,
                createdAt = notification.createdAt,
                sourceTaskId = notification.sourceTaskId?.toString()
            )
    }
}

data class DashboardWorkloadSummaryResponse(
    val assignedToUser: Long,
    val assignedToRole: Long,
    val unassigned: Long
) {
    companion object {
        fun from(summary: DashboardWorkloadSummary): DashboardWorkloadSummaryResponse =
            DashboardWorkloadSummaryResponse(
                assignedToUser = summary.assignedToUser,
                assignedToRole = summary.assignedToRole,
                unassigned = summary.unassigned
            )
    }
}
