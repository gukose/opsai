package com.hotelopai.dashboard.api

import com.hotelopai.dashboard.application.TaskCreatedInRangeReport
import com.hotelopai.dashboard.application.TaskCreatedInRangeSlaReport
import com.hotelopai.dashboard.application.TaskCurrentSnapshotReport
import com.hotelopai.dashboard.application.TaskCurrentSnapshotSlaReport
import com.hotelopai.dashboard.application.TaskReportBucket
import com.hotelopai.dashboard.application.TaskReportingSummary
import com.hotelopai.dashboard.application.TaskReportingWindow
import java.time.Instant

data class TaskReportingResponse(
    val hotelId: String,
    val range: String,
    val generatedAt: Instant,
    val window: TaskReportingWindowResponse,
    val createdInRange: TaskCreatedInRangeReportResponse,
    val currentSnapshot: TaskCurrentSnapshotReportResponse
) {
    companion object {
        fun from(summary: TaskReportingSummary): TaskReportingResponse =
            TaskReportingResponse(
                hotelId = summary.hotelId.toString(),
                range = summary.range.wireValue,
                generatedAt = summary.generatedAt,
                window = TaskReportingWindowResponse.from(summary.window),
                createdInRange = TaskCreatedInRangeReportResponse.from(summary.createdInRange),
                currentSnapshot = TaskCurrentSnapshotReportResponse.from(summary.currentSnapshot)
            )
    }
}

data class TaskReportingWindowResponse(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val timeBasis: String
) {
    companion object {
        fun from(window: TaskReportingWindow): TaskReportingWindowResponse =
            TaskReportingWindowResponse(
                startInclusive = window.startInclusive,
                endExclusive = window.endExclusive,
                timeBasis = window.timeBasis
            )
    }
}

data class TaskCreatedInRangeReportResponse(
    val total: Long,
    val byType: List<TaskReportBucketResponse>,
    val byStatus: List<TaskReportBucketResponse>,
    val byPriority: List<TaskReportBucketResponse>,
    val sla: TaskCreatedInRangeSlaReportResponse
) {
    companion object {
        fun from(report: TaskCreatedInRangeReport): TaskCreatedInRangeReportResponse =
            TaskCreatedInRangeReportResponse(
                total = report.total,
                byType = report.byType.map(TaskReportBucketResponse::from),
                byStatus = report.byStatus.map(TaskReportBucketResponse::from),
                byPriority = report.byPriority.map(TaskReportBucketResponse::from),
                sla = TaskCreatedInRangeSlaReportResponse.from(report.sla)
            )
    }
}

data class TaskCurrentSnapshotReportResponse(
    val active: Long,
    val byStatus: List<TaskReportBucketResponse>,
    val byPriority: List<TaskReportBucketResponse>,
    val sla: TaskCurrentSnapshotSlaReportResponse
) {
    companion object {
        fun from(report: TaskCurrentSnapshotReport): TaskCurrentSnapshotReportResponse =
            TaskCurrentSnapshotReportResponse(
                active = report.active,
                byStatus = report.byStatus.map(TaskReportBucketResponse::from),
                byPriority = report.byPriority.map(TaskReportBucketResponse::from),
                sla = TaskCurrentSnapshotSlaReportResponse.from(report.sla)
            )
    }
}

data class TaskReportBucketResponse(
    val key: String,
    val count: Long
) {
    companion object {
        fun from(bucket: TaskReportBucket): TaskReportBucketResponse =
            TaskReportBucketResponse(key = bucket.key, count = bucket.count)
    }
}

data class TaskCreatedInRangeSlaReportResponse(
    val completedOnTime: Long,
    val completedLate: Long,
    val openWithinSla: Long,
    val openOverdue: Long,
    val cancelled: Long,
    val breached: Long
) {
    companion object {
        fun from(report: TaskCreatedInRangeSlaReport): TaskCreatedInRangeSlaReportResponse =
            TaskCreatedInRangeSlaReportResponse(
                completedOnTime = report.completedOnTime,
                completedLate = report.completedLate,
                openWithinSla = report.openWithinSla,
                openOverdue = report.openOverdue,
                cancelled = report.cancelled,
                breached = report.breached
            )
    }
}

data class TaskCurrentSnapshotSlaReportResponse(
    val dueSoon: Long,
    val overdue: Long
) {
    companion object {
        fun from(report: TaskCurrentSnapshotSlaReport): TaskCurrentSnapshotSlaReportResponse =
            TaskCurrentSnapshotSlaReportResponse(
                dueSoon = report.dueSoon,
                overdue = report.overdue
            )
    }
}
