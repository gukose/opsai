package com.hotelopai.reservation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.outbox.application.OperationalOutboxRepository
import com.hotelopai.outbox.domain.OperationalOutboxAggregateTypes
import com.hotelopai.outbox.domain.OperationalOutboxEvent
import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.StayStatus
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ReservationOutboxPublisher(
    private val outboxRepository: OperationalOutboxRepository,
    private val objectMapper: ObjectMapper,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    fun imported(providerId: String, reservation: Reservation, occurredAt: Instant, sourceDataTimestamp: Instant?) {
        enqueue(
            eventType = OperationalOutboxEventTypes.RESERVATION_IMPORTED,
            providerId = providerId,
            reservation = reservation,
            occurredAt = occurredAt,
            sourceDataTimestamp = sourceDataTimestamp,
            previousReservationStatus = null,
            nextReservationStatus = reservation.reservationStatus,
            previousStayStatus = null,
            nextStayStatus = reservation.stayStatus
        )
    }

    fun updated(providerId: String, previous: Reservation, current: Reservation, occurredAt: Instant, sourceDataTimestamp: Instant?) {
        enqueue(
            eventType = OperationalOutboxEventTypes.RESERVATION_UPDATED,
            providerId = providerId,
            reservation = current,
            occurredAt = occurredAt,
            sourceDataTimestamp = sourceDataTimestamp,
            previousReservationStatus = previous.reservationStatus,
            nextReservationStatus = current.reservationStatus,
            previousStayStatus = previous.stayStatus,
            nextStayStatus = current.stayStatus
        )
        if (previous.reservationStatus != current.reservationStatus) {
            enqueueStatusEvents(providerId, previous, current, occurredAt, sourceDataTimestamp)
        }
        if (previous.stayStatus != current.stayStatus) {
            enqueueStayEvents(providerId, previous, current, occurredAt, sourceDataTimestamp)
        }
        if (previous.roomAssignment?.roomId != current.roomAssignment?.roomId ||
            previous.roomAssignment?.period != current.roomAssignment?.period
        ) {
            enqueue(
                eventType = OperationalOutboxEventTypes.ROOM_ASSIGNMENT_CHANGED,
                providerId = providerId,
                reservation = current,
                occurredAt = occurredAt,
                sourceDataTimestamp = sourceDataTimestamp,
                previousReservationStatus = previous.reservationStatus,
                nextReservationStatus = current.reservationStatus,
                previousStayStatus = previous.stayStatus,
                nextStayStatus = current.stayStatus
            )
        }
    }

    private fun enqueueStatusEvents(
        providerId: String,
        previous: Reservation,
        current: Reservation,
        occurredAt: Instant,
        sourceDataTimestamp: Instant?
    ) {
        enqueue(
            eventType = OperationalOutboxEventTypes.RESERVATION_STATUS_CHANGED,
            providerId = providerId,
            reservation = current,
            occurredAt = occurredAt,
            sourceDataTimestamp = sourceDataTimestamp,
            previousReservationStatus = previous.reservationStatus,
            nextReservationStatus = current.reservationStatus,
            previousStayStatus = previous.stayStatus,
            nextStayStatus = current.stayStatus
        )
        when (current.reservationStatus) {
            ReservationStatus.CANCELLED -> enqueueSimple(OperationalOutboxEventTypes.RESERVATION_CANCELLED, providerId, previous, current, occurredAt, sourceDataTimestamp)
            ReservationStatus.NO_SHOW -> enqueueSimple(OperationalOutboxEventTypes.RESERVATION_MARKED_NO_SHOW, providerId, previous, current, occurredAt, sourceDataTimestamp)
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED -> Unit
        }
    }

    private fun enqueueStayEvents(
        providerId: String,
        previous: Reservation,
        current: Reservation,
        occurredAt: Instant,
        sourceDataTimestamp: Instant?
    ) {
        when (current.stayStatus) {
            StayStatus.IN_HOUSE -> enqueueSimple(OperationalOutboxEventTypes.GUEST_CHECKED_IN, providerId, previous, current, occurredAt, sourceDataTimestamp)
            StayStatus.CHECKED_OUT -> enqueueSimple(OperationalOutboxEventTypes.GUEST_CHECKED_OUT, providerId, previous, current, occurredAt, sourceDataTimestamp)
            StayStatus.NOT_ARRIVED -> Unit
        }
    }

    private fun enqueueSimple(
        eventType: String,
        providerId: String,
        previous: Reservation,
        current: Reservation,
        occurredAt: Instant,
        sourceDataTimestamp: Instant?
    ) {
        enqueue(
            eventType = eventType,
            providerId = providerId,
            reservation = current,
            occurredAt = occurredAt,
            sourceDataTimestamp = sourceDataTimestamp,
            previousReservationStatus = previous.reservationStatus,
            nextReservationStatus = current.reservationStatus,
            previousStayStatus = previous.stayStatus,
            nextStayStatus = current.stayStatus
        )
    }

    private fun enqueue(
        eventType: String,
        providerId: String,
        reservation: Reservation,
        occurredAt: Instant,
        sourceDataTimestamp: Instant?,
        previousReservationStatus: ReservationStatus?,
        nextReservationStatus: ReservationStatus,
        previousStayStatus: StayStatus?,
        nextStayStatus: StayStatus
    ) {
        val now = PersistenceInstant.toPersistencePrecision(occurredAt)
        val payload = ReservationOutboxPayload(
            payloadVersion = ReservationOutboxPayload.VERSION,
            reservationId = reservation.id.value,
            providerId = providerId,
            propertyReference = reservation.propertyId.value,
            occurredAt = now.toString(),
            sourceDataTimestamp = PersistenceInstant.toPersistencePrecisionOrNull(sourceDataTimestamp)?.toString(),
            previousReservationStatus = previousReservationStatus?.name,
            nextReservationStatus = nextReservationStatus.name,
            previousStayStatus = previousStayStatus?.name,
            nextStayStatus = nextStayStatus.name
        )
        val event = OperationalOutboxEvent(
            eventType = eventType,
            aggregateType = OperationalOutboxAggregateTypes.RESERVATION,
            aggregateId = reservation.id.value,
            hotelId = SYSTEM_HOTEL_ID,
            payloadJson = objectMapper.writeValueAsString(payload),
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now
        )

        try {
            outboxRepository.save(event)
            record(eventType, "success", "none")
        } catch (_: DuplicateKeyException) {
            val existing = outboxRepository.findByEventAggregate(
                eventType = eventType,
                aggregateType = OperationalOutboxAggregateTypes.RESERVATION,
                aggregateId = reservation.id.value
            )
            if (existing == null) {
                record(eventType, "failed", "duplicate_without_existing_event")
                throw DuplicateReservationOutboxEventException()
            }
            record(eventType, "duplicate", "event_already_exists")
        }
    }

    private fun record(eventType: String, outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.outbox.event.total",
            "operation" to "enqueue",
            "event_type" to eventType.lowercase(),
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    companion object {
        private val SYSTEM_HOTEL_ID = UUID(0L, 0L)
    }
}

data class ReservationOutboxPayload(
    val payloadVersion: Int,
    val reservationId: UUID,
    val providerId: String,
    val propertyReference: String,
    val occurredAt: String,
    val sourceDataTimestamp: String?,
    val previousReservationStatus: String?,
    val nextReservationStatus: String,
    val previousStayStatus: String?,
    val nextStayStatus: String
) {
    companion object {
        const val VERSION = 1
    }
}

class DuplicateReservationOutboxEventException : RuntimeException("Duplicate reservation outbox event could not be resolved")
