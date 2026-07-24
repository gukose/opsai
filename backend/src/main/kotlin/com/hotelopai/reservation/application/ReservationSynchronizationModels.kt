package com.hotelopai.reservation.application

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.PropertyId
import java.time.Instant

enum class ReservationSyncStatus {
    NEVER_SYNCED,
    RUNNING,
    SUCCEEDED,
    FAILED
}

enum class ReservationSyncOutcome {
    CREATED,
    UPDATED,
    UNCHANGED,
    SKIPPED_STALE,
    CONFLICT
}

data class ReservationSyncState(
    val providerId: String,
    val propertyId: PropertyId,
    val status: ReservationSyncStatus,
    val syncCursor: String? = null,
    val lastAttemptedAt: Instant? = null,
    val lastSuccessfulAt: Instant? = null,
    val lastFailureCategory: PmsFailureCategory? = null,
    val sourceDataTimestamp: Instant? = null,
    val window: DateRange? = null,
    val fetchedCount: Int = 0,
    val createdCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val staleCount: Int = 0,
    val conflictCount: Int = 0,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ReservationSyncSummary(
    val providerId: String,
    val propertyId: String,
    val status: ReservationSyncStatus,
    val fetchedCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val staleCount: Int,
    val conflictCount: Int,
    val failureCategory: PmsFailureCategory? = null
)

data class ReservationSynchronizationCommand(
    val propertyId: PropertyId,
    val dateRange: DateRange,
    val sourceDataTimestamp: Instant? = null
)

interface ReservationSyncStateRepository {
    fun find(providerId: String, propertyId: PropertyId): ReservationSyncState?
    fun save(state: ReservationSyncState): ReservationSyncState
}
