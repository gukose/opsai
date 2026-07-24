package com.hotelopai.reservation.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.application.UnsupportedPmsCapabilityException
import com.hotelopai.pms.domain.PmsProviderAuthenticationException
import com.hotelopai.pms.domain.PmsProviderCircuitOpenException
import com.hotelopai.pms.domain.PmsProviderConfigurationFailureException
import com.hotelopai.pms.domain.PmsProviderInvalidRequestException
import com.hotelopai.pms.domain.PmsProviderMalformedResponseException
import com.hotelopai.pms.domain.PmsProviderPermissionException
import com.hotelopai.pms.domain.PmsProviderRateLimitException
import com.hotelopai.pms.domain.PmsProviderResourceNotFoundException
import com.hotelopai.pms.domain.PmsProviderTimeoutException
import com.hotelopai.pms.domain.PmsProviderUnavailableException
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@EnableConfigurationProperties(ReservationSyncProperties::class)
class ReservationSynchronizationService(
    private val pmsProviderRegistry: PmsProviderRegistry,
    private val mapper: PmsReservationMapper,
    private val reservationRepository: ReservationRepository,
    private val syncStateRepository: ReservationSyncStateRepository,
    private val outboxPublisher: ReservationOutboxPublisher,
    private val mergePolicy: ReservationSnapshotMergePolicy,
    private val clock: Clock,
    private val properties: ReservationSyncProperties = ReservationSyncProperties(),
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional
    fun synchronize(command: ReservationSynchronizationCommand): ReservationSyncSummary {
        val startedAt = PersistenceInstant.now(clock)
        val providerId = pmsProviderRegistry.activeProviderId()
        val timer = observability.startTimer()
        saveRunningState(providerId, command, startedAt)

        try {
            val provider = pmsProviderRegistry.activeProviderRequiring(PmsCapability.RESERVATION_LOOKUP)
            if (!provider.capabilities.supports(PmsCapability.GUEST_LOOKUP)) {
                throw UnsupportedPmsCapabilityException(provider.id.value, PmsCapability.GUEST_LOOKUP)
            }

            val guestsById = provider.listGuests().associateBy { it.id }
            val mapped = provider.listReservations()
                .asSequence()
                .map { mapper.toReservation(it, guestsById, command.propertyId, startedAt) }
                .filter { it.propertyId == command.propertyId && it.stayPeriod.overlaps(command.dateRange) }
                .take(properties.normalizedMaxReservationsPerRun() + 1)
                .toList()
            if (mapped.size > properties.normalizedMaxReservationsPerRun()) {
                throw ReservationSyncBoundExceededException(properties.normalizedMaxReservationsPerRun())
            }

            val counts = ReservationSyncCounts()
            mapped.sortedBy { it.externalReference.value }.forEach { reservation ->
                counts.record(upsert(provider.id.value, reservation, command.sourceDataTimestamp, startedAt))
            }
            val summary = counts.toSummary(provider.id.value, command.propertyId)
            syncStateRepository.save(
                state(
                    providerId = provider.id.value,
                    command = command,
                    status = ReservationSyncStatus.SUCCEEDED,
                    now = startedAt,
                    sourceDataTimestamp = command.sourceDataTimestamp,
                    counts = counts
                )
            )
            recordMetrics(provider.id.value, summary, "success", "none", timer)
            return summary
        } catch (exception: RuntimeException) {
            val failureCategory = exception.toFailureCategory()
            val summary = ReservationSyncSummary(
                providerId = providerId,
                propertyId = command.propertyId.value,
                status = ReservationSyncStatus.FAILED,
                fetchedCount = 0,
                createdCount = 0,
                updatedCount = 0,
                unchangedCount = 0,
                staleCount = 0,
                conflictCount = 0,
                failureCategory = failureCategory
            )
            syncStateRepository.save(
                state(
                    providerId = providerId,
                    command = command,
                    status = ReservationSyncStatus.FAILED,
                    now = startedAt,
                    sourceDataTimestamp = command.sourceDataTimestamp,
                    failureCategory = failureCategory
                )
            )
            recordMetrics(providerId, summary, "failed", failureCategory.name.lowercase(), timer)
            throw exception
        }
    }

    private fun saveRunningState(providerId: String, command: ReservationSynchronizationCommand, now: Instant) {
        syncStateRepository.save(
            state(
                providerId = providerId,
                command = command,
                status = ReservationSyncStatus.RUNNING,
                now = now,
                sourceDataTimestamp = command.sourceDataTimestamp
            )
        )
    }

    private fun upsert(
        providerId: String,
        incoming: Reservation,
        sourceDataTimestamp: Instant?,
        now: Instant
    ): ReservationSyncOutcome {
        val existing = reservationRepository.findSnapshotByMatch(
            providerId = providerId,
            externalReference = incoming.externalReference,
            propertyId = incoming.propertyId
        )
        if (existing == null) {
            val decision = mergePolicy.merge(providerId, incoming, existing, sourceDataTimestamp, now)
            val snapshot = requireNotNull(decision.snapshot)
            reservationRepository.saveSnapshot(snapshot)
            outboxPublisher.imported(providerId, snapshot.reservation, now, sourceDataTimestamp)
            return decision.outcome
        }

        val decision = mergePolicy.merge(providerId, incoming, existing, sourceDataTimestamp, now)
        if (decision.outcome != ReservationSyncOutcome.UPDATED) {
            return decision.outcome
        }
        val snapshot = requireNotNull(decision.snapshot)
        reservationRepository.saveSnapshot(snapshot)
        outboxPublisher.updated(providerId, requireNotNull(decision.previousReservation), snapshot.reservation, now, sourceDataTimestamp)
        return decision.outcome
    }

    private fun state(
        providerId: String,
        command: ReservationSynchronizationCommand,
        status: ReservationSyncStatus,
        now: Instant,
        sourceDataTimestamp: Instant?,
        failureCategory: PmsFailureCategory? = null,
        counts: ReservationSyncCounts = ReservationSyncCounts()
    ): ReservationSyncState {
        val existing = syncStateRepository.find(providerId, command.propertyId)
        return ReservationSyncState(
            providerId = providerId,
            propertyId = command.propertyId,
            status = status,
            syncCursor = existing?.syncCursor,
            lastAttemptedAt = now,
            lastSuccessfulAt = if (status == ReservationSyncStatus.SUCCEEDED) now else existing?.lastSuccessfulAt,
            lastFailureCategory = failureCategory,
            sourceDataTimestamp = sourceDataTimestamp,
            window = command.dateRange,
            fetchedCount = counts.fetched,
            createdCount = counts.created,
            updatedCount = counts.updated,
            unchangedCount = counts.unchanged,
            staleCount = counts.stale,
            conflictCount = counts.conflict,
            version = existing?.version ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
    }

    private fun recordMetrics(
        providerId: String,
        summary: ReservationSyncSummary,
        outcome: String,
        reasonCode: String,
        timer: io.micrometer.core.instrument.Timer.Sample?
    ) {
        observability.incrementCounter(
            "hotelopai.reservation.sync.runs.total",
            "provider" to providerId,
            "operation" to "reservation_sync",
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
        observability.incrementCounter("hotelopai.reservation.sync.fetched.total", summary.fetchedCount.toDouble(), "provider" to providerId, "operation" to "reservation_sync", "outcome" to outcome)
        observability.incrementCounter("hotelopai.reservation.sync.created.total", summary.createdCount.toDouble(), "provider" to providerId, "operation" to "reservation_sync", "outcome" to outcome)
        observability.incrementCounter("hotelopai.reservation.sync.updated.total", summary.updatedCount.toDouble(), "provider" to providerId, "operation" to "reservation_sync", "outcome" to outcome)
        observability.incrementCounter("hotelopai.reservation.sync.unchanged.total", summary.unchangedCount.toDouble(), "provider" to providerId, "operation" to "reservation_sync", "outcome" to outcome)
        observability.incrementCounter("hotelopai.reservation.sync.stale.total", summary.staleCount.toDouble(), "provider" to providerId, "operation" to "reservation_sync", "outcome" to outcome)
        observability.incrementCounter("hotelopai.reservation.sync.conflict.total", summary.conflictCount.toDouble(), "provider" to providerId, "operation" to "reservation_sync", "outcome" to outcome)
        observability.stopTimer(
            timer,
            "hotelopai.reservation.sync.duration",
            "provider" to providerId,
            "operation" to "reservation_sync",
            "outcome" to outcome
        )
    }

    private fun RuntimeException.toFailureCategory(): PmsFailureCategory =
        when (this) {
            is UnsupportedPmsCapabilityException -> PmsFailureCategory.CONFIGURATION
            is ReservationSyncBoundExceededException -> PmsFailureCategory.VALIDATION
            is ReservationMappingException -> PmsFailureCategory.MALFORMED_RESPONSE
            is PmsProviderAuthenticationException -> PmsFailureCategory.AUTHENTICATION
            is PmsProviderPermissionException -> PmsFailureCategory.PERMISSION
            is PmsProviderInvalidRequestException -> PmsFailureCategory.VALIDATION
            is PmsProviderResourceNotFoundException -> PmsFailureCategory.NOT_FOUND
            is PmsProviderRateLimitException -> PmsFailureCategory.RATE_LIMIT
            is PmsProviderTimeoutException -> PmsFailureCategory.TIMEOUT
            is PmsProviderMalformedResponseException -> PmsFailureCategory.MALFORMED_RESPONSE
            is PmsProviderConfigurationFailureException -> PmsFailureCategory.CONFIGURATION
            is PmsProviderCircuitOpenException -> PmsFailureCategory.CIRCUIT_OPEN
            is PmsProviderUnavailableException -> PmsFailureCategory.PROVIDER_UNAVAILABLE
            else -> PmsFailureCategory.UNKNOWN
        }

    private class ReservationSyncCounts {
        var fetched: Int = 0
            private set
        var created: Int = 0
            private set
        var updated: Int = 0
            private set
        var unchanged: Int = 0
            private set
        var stale: Int = 0
            private set
        var conflict: Int = 0
            private set

        fun record(outcome: ReservationSyncOutcome) {
            fetched += 1
            when (outcome) {
                ReservationSyncOutcome.CREATED -> created += 1
                ReservationSyncOutcome.UPDATED -> updated += 1
                ReservationSyncOutcome.UNCHANGED -> unchanged += 1
                ReservationSyncOutcome.SKIPPED_STALE -> stale += 1
                ReservationSyncOutcome.CONFLICT -> conflict += 1
            }
        }

        fun toSummary(providerId: String, propertyId: PropertyId): ReservationSyncSummary =
            ReservationSyncSummary(
                providerId = providerId,
                propertyId = propertyId.value,
                status = ReservationSyncStatus.SUCCEEDED,
                fetchedCount = fetched,
                createdCount = created,
                updatedCount = updated,
                unchangedCount = unchanged,
                staleCount = stale,
                conflictCount = conflict
            )
    }
}

class ReservationSyncBoundExceededException(
    maxReservations: Int
) : RuntimeException("Reservation synchronization exceeded the configured maximum of $maxReservations reservations.")
