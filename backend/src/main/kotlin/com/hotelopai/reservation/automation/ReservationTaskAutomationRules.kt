package com.hotelopai.reservation.automation

import com.hotelopai.outbox.domain.OperationalOutboxEventTypes
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.StayStatus
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

abstract class BaseReservationTaskAutomationRule(
    override val id: ReservationTaskAutomationRuleId,
    override val version: Int,
    override val supportedEventTypes: Set<String>
) : ReservationTaskAutomationRule {
    protected fun proposal(
        context: ReservationTaskAutomationContext,
        title: String,
        description: String,
        intentType: TaskIntentType,
        priority: TaskPriority,
        dueAt: Instant,
        roomNumber: String? = context.reservation.roomAssignment?.roomId?.value,
        safeMetadata: Map<String, String> = emptyMap()
    ): ReservationTaskProposal =
        ReservationTaskProposal(
            ruleId = id,
            ruleVersion = version,
            triggerEventType = context.outboxEvent.eventType,
            intentType = intentType,
            title = title,
            description = description,
            roomNumber = roomNumber,
            priority = context.dueDatePolicy.priority(id, priority),
            dueAt = dueAt,
            deduplicationKey = dedupe(context),
            safeMetadata = mapOf("rule" to id.value, "eventType" to context.outboxEvent.eventType) + safeMetadata
        )

    protected fun defaultDue(context: ReservationTaskAutomationContext, date: LocalDate = context.reservation.stayPeriod.arrival): Instant =
        context.dueDatePolicy.dueForDate(id, date, ReservationTaskAutomationDueKind.DEFAULT)

    protected fun sameDayDue(context: ReservationTaskAutomationContext): Instant =
        context.dueDatePolicy.dueForDate(
            id,
            context.now.atZone(context.dueDatePolicy.policyFor(id).timezone).toLocalDate(),
            ReservationTaskAutomationDueKind.SAME_DAY
        )

    protected fun blockedByPolicy(context: ReservationTaskAutomationContext): Boolean =
        context.dueDatePolicy.policyFor(id).eventTooOld(context.occurredAt, context.now)

    protected fun dedupe(context: ReservationTaskAutomationContext): String =
        sha256("${id.value}:$version:${context.payload.reservationId}:${context.outboxEvent.eventType}:${context.payload.occurredAt}:${stateMarker(context.reservation)}")

    private fun stateMarker(reservation: Reservation): String =
        "${reservation.reservationStatus}:${reservation.stayStatus}:${reservation.roomAssignment?.roomId?.value ?: "unassigned"}:${reservation.modifiedAt}"

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

@Component
class UpcomingArrivalPreparationRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("upcoming-arrival-preparation"),
    1,
    setOf(OperationalOutboxEventTypes.RESERVATION_IMPORTED, OperationalOutboxEventTypes.RESERVATION_UPDATED)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> {
        val reservation = context.reservation
        if (reservation.reservationStatus != ReservationStatus.CONFIRMED || reservation.stayStatus != StayStatus.NOT_ARRIVED) return emptyList()
        val policy = context.dueDatePolicy.policyFor(id)
        if (blockedByPolicy(context)) return emptyList()
        if (reservation.stayPeriod.arrival.isBefore(context.now.atZone(policy.timezone).toLocalDate())) return emptyList()
        return listOf(
            proposal(
                context,
                title = "Prepare arrival room",
                description = "Review room readiness for upcoming confirmed arrival.",
                intentType = TaskIntentType.HOUSEKEEPING,
                priority = TaskPriority.MEDIUM,
                dueAt = defaultDue(context)
            )
        )
    }
}

@Component
class SameDayDepartureFollowUpRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("same-day-departure-follow-up"),
    1,
    setOf(OperationalOutboxEventTypes.RESERVATION_IMPORTED, OperationalOutboxEventTypes.RESERVATION_UPDATED)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> {
        val policy = context.dueDatePolicy.policyFor(id)
        if (blockedByPolicy(context)) return emptyList()
        val today = context.now.atZone(policy.timezone).toLocalDate()
        val checkoutDate = context.reservation.stayPeriod.departure
        if (checkoutDate != today) return emptyList()
        if (context.reservation.reservationStatus == ReservationStatus.CANCELLED) return emptyList()
        return listOf(
            proposal(
                context,
                title = "Review same-day departure",
                description = "Confirm operational follow-up for today's departure.",
                intentType = TaskIntentType.HOUSEKEEPING,
                priority = TaskPriority.MEDIUM,
                dueAt = sameDayDue(context)
            )
        )
    }
}

@Component
class RoomAssignmentChangeReviewRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("room-assignment-change-review"),
    1,
    setOf(OperationalOutboxEventTypes.ROOM_ASSIGNMENT_CHANGED)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> =
        if (blockedByPolicy(context)) {
            emptyList()
        } else {
        listOf(
            proposal(
                context,
                title = "Review room assignment change",
                description = "Check operational tasks affected by the room assignment change.",
                intentType = TaskIntentType.SHIFT_HANDOVER,
                priority = TaskPriority.MEDIUM,
                dueAt = sameDayDue(context)
            )
        )
        }
}

@Component
class ReservationCancellationCleanupRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("reservation-cancellation-cleanup"),
    1,
    setOf(OperationalOutboxEventTypes.RESERVATION_CANCELLED)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> =
        if (blockedByPolicy(context)) {
            emptyList()
        } else {
        listOf(
            proposal(
                context,
                title = "Review cancelled reservation operations",
                description = "Clean up operational tasks related to a cancelled reservation.",
                intentType = TaskIntentType.SHIFT_HANDOVER,
                priority = TaskPriority.MEDIUM,
                dueAt = sameDayDue(context)
            )
        )
        }
}

@Component
class NoShowOperationalReviewRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("no-show-operational-review"),
    1,
    setOf(OperationalOutboxEventTypes.RESERVATION_MARKED_NO_SHOW)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> =
        if (blockedByPolicy(context)) {
            emptyList()
        } else {
        listOf(
            proposal(
                context,
                title = "Review no-show reservation",
                description = "Review operational follow-up for a no-show reservation.",
                intentType = TaskIntentType.SHIFT_HANDOVER,
                priority = TaskPriority.HIGH,
                dueAt = sameDayDue(context),
                roomNumber = null
            )
        )
        }
}

@Component
class GuestCheckInFollowUpRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("guest-check-in-follow-up"),
    1,
    setOf(OperationalOutboxEventTypes.GUEST_CHECKED_IN)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> =
        if (blockedByPolicy(context)) {
            emptyList()
        } else {
        listOf(
            proposal(
                context,
                title = "Check in-house follow-up",
                description = "Confirm operational follow-up after guest check-in.",
                intentType = TaskIntentType.GUEST_REQUEST,
                priority = TaskPriority.LOW,
                dueAt = sameDayDue(context)
            )
        )
        }
}

@Component
class GuestCheckoutFollowUpRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("guest-checkout-follow-up"),
    1,
    setOf(OperationalOutboxEventTypes.GUEST_CHECKED_OUT)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> =
        if (blockedByPolicy(context)) {
            emptyList()
        } else {
        listOf(
            proposal(
                context,
                title = "Departure Cleaning",
                description = "Clean and prepare the checked-out room for inspection.",
                intentType = TaskIntentType.HOUSEKEEPING,
                priority = TaskPriority.MEDIUM,
                dueAt = sameDayDue(context),
                safeMetadata = mapOf("housekeeping_type" to "DEPARTURE_CLEANING", "inspection_required" to "true")
            )
        )
}

@Component
class StayoverCleaningRule : BaseReservationTaskAutomationRule(
    ReservationTaskAutomationRuleId("stayover-cleaning"),
    1,
    setOf(OperationalOutboxEventTypes.RESERVATION_IMPORTED, OperationalOutboxEventTypes.RESERVATION_UPDATED)
) {
    override fun evaluate(context: ReservationTaskAutomationContext): List<ReservationTaskProposal> {
        if (blockedByPolicy(context) || context.reservation.stayStatus != StayStatus.IN_HOUSE) return emptyList()
        val room = context.reservation.roomAssignment?.roomId?.value ?: return emptyList()
        val today = context.now.atZone(context.dueDatePolicy.policyFor(id).timezone).toLocalDate()
        if (today.isBefore(context.reservation.stayPeriod.arrival) || !today.isBefore(context.reservation.stayPeriod.departure)) return emptyList()
        return listOf(proposal(
            context,
            title = "Stayover Cleaning",
            description = "Complete the configured in-house stayover service.",
            intentType = TaskIntentType.HOUSEKEEPING,
            priority = TaskPriority.MEDIUM,
            dueAt = sameDayDue(context),
            roomNumber = room,
            safeMetadata = mapOf("housekeeping_type" to "STAYOVER_CLEANING", "inspection_required" to "false")
        ))
    }
}
}
