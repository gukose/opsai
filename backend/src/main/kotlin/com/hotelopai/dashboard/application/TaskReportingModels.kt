package com.hotelopai.dashboard.application

import com.hotelopai.dashboard.domain.DashboardTimeRange
import java.time.Instant
import java.util.UUID

data class TaskReportingSummary(
    val hotelId: UUID,
    val range: DashboardTimeRange,
    val generatedAt: Instant,
    val window: TaskReportingWindow,
    val createdInRange: TaskCreatedInRangeReport,
    val currentSnapshot: TaskCurrentSnapshotReport
)

data class TaskReportingWindow(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val timeBasis: String = "UTC"
)

data class TaskCreatedInRangeReport(
    val total: Long,
    val byType: List<TaskReportBucket>,
    val byStatus: List<TaskReportBucket>,
    val byPriority: List<TaskReportBucket>,
    val sla: TaskCreatedInRangeSlaReport
)

data class TaskCurrentSnapshotReport(
    val active: Long,
    val byStatus: List<TaskReportBucket>,
    val byPriority: List<TaskReportBucket>,
    val sla: TaskCurrentSnapshotSlaReport
)

data class TaskReportBucket(
    val key: String,
    val count: Long
)

data class TaskCreatedInRangeSlaReport(
    val completedOnTime: Long,
    val completedLate: Long,
    val openWithinSla: Long,
    val openOverdue: Long,
    val cancelled: Long,
    val breached: Long
)

data class TaskCurrentSnapshotSlaReport(
    val dueSoon: Long,
    val overdue: Long
)
