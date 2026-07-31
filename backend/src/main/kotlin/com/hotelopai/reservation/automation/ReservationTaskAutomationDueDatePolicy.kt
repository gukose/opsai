package com.hotelopai.reservation.automation

import com.hotelopai.task.domain.TaskPriority
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class ReservationTaskAutomationDueDatePolicy(
    private val properties: ReservationTaskAutomationProperties,
    private val clock: Clock
) {
    fun policyFor(ruleId: ReservationTaskAutomationRuleId): ReservationTaskAutomationRulePolicy {
        val override = properties.rules[ruleId.value]
        return ReservationTaskAutomationRulePolicy(
            ruleId = ruleId,
            enabled = properties.ruleEnabled(ruleId),
            priority = override?.priority,
            dueTime = override?.dueTime,
            dueDateOffsetDays = override?.dueDateOffsetDays ?: 0,
            minimumLeadTime = override?.minimumLeadTime ?: properties.minimumLeadTime,
            timezone = override?.timezone ?: properties.timezone,
            maximumTriggerAge = override?.maximumTriggerAge,
            clampPastDue = override?.clampPastDue ?: true
        )
    }

    fun dueForDate(ruleId: ReservationTaskAutomationRuleId, date: LocalDate, fallbackKind: ReservationTaskAutomationDueKind): Instant {
        val policy = policyFor(ruleId)
        val dueTime = policy.dueTime ?: when (fallbackKind) {
            ReservationTaskAutomationDueKind.DEFAULT -> properties.defaultDueTime
            ReservationTaskAutomationDueKind.SAME_DAY -> properties.sameDayDueTime
        }
        val due = date
            .plusDays(policy.dueDateOffsetDays)
            .atTime(dueTime)
            .atZone(policy.timezone)
            .toInstant()
        return policy.clamp(due, Instant.now(clock))
    }

    fun priority(ruleId: ReservationTaskAutomationRuleId, defaultPriority: TaskPriority): TaskPriority =
        policyFor(ruleId).priority ?: defaultPriority
}

data class ReservationTaskAutomationRulePolicy(
    val ruleId: ReservationTaskAutomationRuleId,
    val enabled: Boolean,
    val priority: TaskPriority?,
    val dueTime: java.time.LocalTime?,
    val dueDateOffsetDays: Long,
    val minimumLeadTime: java.time.Duration,
    val timezone: java.time.ZoneId,
    val maximumTriggerAge: java.time.Duration?,
    val clampPastDue: Boolean
) {
    fun eventTooOld(occurredAt: Instant, now: Instant): Boolean =
        maximumTriggerAge?.let { occurredAt.plus(it).isBefore(now) } ?: false

    fun clamp(candidate: Instant, now: Instant): Instant =
        if (clampPastDue && candidate.isBefore(now.plus(minimumLeadTime))) {
            now.plus(minimumLeadTime)
        } else {
            candidate
        }
}

enum class ReservationTaskAutomationDueKind {
    DEFAULT,
    SAME_DAY
}
