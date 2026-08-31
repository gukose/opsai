package com.hotelopai.housekeeping.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class HousekeepingWorkflowType { DEPARTURE_CLEANING, STAYOVER_CLEANING, VIP_PREPARATION }
enum class HousekeepingStatus { CREATED, ASSIGNED, ACCEPTED, STARTED, PAUSED, WAITING, INSPECTION, REWORK, COMPLETED, CLOSED }
enum class InspectionResult { PASS, REJECT }

data class HousekeepingWorkflow(
    val id: UUID,
    val hotelId: UUID,
    val taskId: UUID,
    val type: HousekeepingWorkflowType,
    val roomNumber: String,
    val status: HousekeepingStatus,
    val inspectionRequired: Boolean,
    val acceptedAt: Instant? = null,
    val startedAt: Instant? = null,
    val pausedAt: Instant? = null,
    val resumedAt: Instant? = null,
    val cleaningCompletedAt: Instant? = null,
    val inspectionStartedAt: Instant? = null,
    val inspectionCompletedAt: Instant? = null,
    val closedAt: Instant? = null,
    val workingSeconds: Long = 0,
    val pausedSeconds: Long = 0,
    val activeSegmentStartedAt: Instant? = null,
    val pauseSegmentStartedAt: Instant? = null,
    val idempotencyKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val templateId: UUID? = null,
    val templateVersion: Int? = null
) {
    fun assign(now: Instant) = transition(setOf(HousekeepingStatus.CREATED, HousekeepingStatus.REWORK), HousekeepingStatus.ASSIGNED, now)

    fun accept(now: Instant): HousekeepingWorkflow {
        require(status == HousekeepingStatus.ASSIGNED) { "Housekeeping workflow must be ASSIGNED before acceptance" }
        return copy(status = HousekeepingStatus.ACCEPTED, acceptedAt = acceptedAt ?: now, updatedAt = now)
    }

    fun start(now: Instant): HousekeepingWorkflow {
        require(status in setOf(HousekeepingStatus.CREATED, HousekeepingStatus.ACCEPTED, HousekeepingStatus.ASSIGNED, HousekeepingStatus.REWORK)) { "Housekeeping workflow cannot start from $status" }
        return copy(status = HousekeepingStatus.STARTED, startedAt = startedAt ?: now, activeSegmentStartedAt = now, pauseSegmentStartedAt = null, updatedAt = now)
    }

    fun pause(now: Instant, waiting: Boolean = false): HousekeepingWorkflow {
        require(status == HousekeepingStatus.STARTED) { "Only active housekeeping work can be paused" }
        return copy(
            status = if (waiting) HousekeepingStatus.WAITING else HousekeepingStatus.PAUSED,
            pausedAt = now,
            workingSeconds = workingSeconds + elapsed(activeSegmentStartedAt, now),
            activeSegmentStartedAt = null,
            pauseSegmentStartedAt = now,
            updatedAt = now
        )
    }

    fun resume(now: Instant): HousekeepingWorkflow {
        require(status in setOf(HousekeepingStatus.PAUSED, HousekeepingStatus.WAITING)) { "Only paused/waiting housekeeping work can resume" }
        return copy(
            status = HousekeepingStatus.STARTED,
            resumedAt = now,
            pausedSeconds = pausedSeconds + elapsed(pauseSegmentStartedAt, now),
            pauseSegmentStartedAt = null,
            activeSegmentStartedAt = now,
            updatedAt = now
        )
    }

    fun finishCleaning(now: Instant): HousekeepingWorkflow {
        require(status == HousekeepingStatus.STARTED || status == HousekeepingStatus.REWORK) { "Cleaning can only finish while active or in rework" }
        val next = if (inspectionRequired) HousekeepingStatus.INSPECTION else HousekeepingStatus.COMPLETED
        return copy(
            status = next,
            cleaningCompletedAt = now,
            inspectionStartedAt = if (inspectionRequired) now else inspectionStartedAt,
            workingSeconds = workingSeconds + elapsed(activeSegmentStartedAt, now),
            activeSegmentStartedAt = null,
            updatedAt = now
        )
    }

    fun inspect(result: InspectionResult, now: Instant): HousekeepingWorkflow {
        require(status == HousekeepingStatus.INSPECTION) { "Workflow is not awaiting inspection" }
        return if (result == InspectionResult.PASS) {
            copy(status = HousekeepingStatus.COMPLETED, inspectionCompletedAt = now, updatedAt = now)
        } else {
            copy(status = HousekeepingStatus.REWORK, inspectionCompletedAt = now, updatedAt = now)
        }
    }

    fun close(now: Instant): HousekeepingWorkflow {
        require(status == HousekeepingStatus.COMPLETED) { "Only completed housekeeping work can close" }
        return copy(status = HousekeepingStatus.CLOSED, closedAt = now, updatedAt = now)
    }

    fun actualWorkingDuration(now: Instant = updatedAt): Duration = Duration.ofSeconds(workingSeconds + elapsed(activeSegmentStartedAt, now))
    fun totalPausedDuration(now: Instant = updatedAt): Duration = Duration.ofSeconds(pausedSeconds + elapsed(pauseSegmentStartedAt, now))

    private fun transition(from: Set<HousekeepingStatus>, to: HousekeepingStatus, now: Instant): HousekeepingWorkflow {
        require(status in from) { "Housekeeping workflow cannot transition from $status to $to" }
        return copy(status = to, updatedAt = now)
    }

    private fun elapsed(from: Instant?, to: Instant): Long = from?.let { Duration.between(it, to).seconds.coerceAtLeast(0) } ?: 0
}

data class InspectionAnswer(val checklistItemId: UUID, val passed: Boolean, val note: String? = null)
data class HousekeepingInspection(
    val id: UUID,
    val workflowId: UUID,
    val inspectorUserId: UUID,
    val attempt: Int,
    val result: InspectionResult,
    val rejectionReason: String?,
    val qualityScore: Int?,
    val answers: List<InspectionAnswer>,
    val startedAt: Instant,
    val completedAt: Instant
) {
    init {
        require(result != InspectionResult.REJECT || !rejectionReason.isNullOrBlank()) { "Rejection reason is required" }
        require(qualityScore == null || qualityScore in 0..100) { "Quality score must be between 0 and 100" }
    }
}
