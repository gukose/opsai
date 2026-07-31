package com.hotelopai.reservation.automation

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@ConfigurationProperties("ops.ai.reservation.task-automation")
data class ReservationTaskAutomationProperties(
    val enabled: Boolean = false,
    val hotelId: UUID? = null,
    val batchSize: Int = 10,
    val maxAttempts: Int = 3,
    val retryDelay: Duration = Duration.ofMinutes(5),
    val enabledRuleIds: Set<String> = emptySet(),
    val defaultDueTime: LocalTime = LocalTime.of(12, 0),
    val sameDayDueTime: LocalTime = LocalTime.of(16, 0),
    val followUpDelay: Duration = Duration.ofHours(2),
    val timezone: ZoneId = ZoneId.of("UTC"),
    val minimumLeadTime: Duration = Duration.ofHours(1),
    val allowedProfiles: List<String> = emptyList(),
    val processorId: String = "reservation-task-automation",
    val rules: Map<String, ReservationTaskAutomationRulePolicyProperties> = emptyMap(),
    val schedule: ReservationTaskAutomationScheduleProperties = ReservationTaskAutomationScheduleProperties()
) {
    init {
        if (enabled) {
            require(hotelId != null && hotelId != UUID(0L, 0L)) {
                "reservation task automation hotel id must be configured when enabled"
            }
        }
        require(batchSize in 1..100) { "reservation task automation batch size must be between 1 and 100" }
        require(maxAttempts in 1..10) { "reservation task automation max attempts must be between 1 and 10" }
        require(!retryDelay.isNegative && !retryDelay.isZero) {
            "reservation task automation retry delay must be positive"
        }
        require(!minimumLeadTime.isNegative) {
            "reservation task automation minimum lead time must not be negative"
        }
        require(processorId.isNotBlank()) { "reservation task automation processor id must not be blank" }
    }

    fun ruleEnabled(ruleId: ReservationTaskAutomationRuleId): Boolean =
        (enabledRuleIds.isEmpty() || ruleId.value in enabledRuleIds) &&
            (rules[ruleId.value]?.enabled ?: true)
}

data class ReservationTaskAutomationRulePolicyProperties(
    val enabled: Boolean = true,
    val priority: com.hotelopai.task.domain.TaskPriority? = null,
    val dueTime: LocalTime? = null,
    val dueDateOffsetDays: Long = 0,
    val minimumLeadTime: Duration? = null,
    val timezone: ZoneId? = null,
    val maximumTriggerAge: Duration? = null,
    val clampPastDue: Boolean = true
) {
    init {
        require(dueDateOffsetDays in -30..30) {
            "reservation task automation rule due date offset must be between -30 and 30 days"
        }
        minimumLeadTime?.let {
            require(!it.isNegative) { "reservation task automation rule minimum lead time must not be negative" }
        }
        maximumTriggerAge?.let {
            require(!it.isNegative && !it.isZero) {
                "reservation task automation rule maximum trigger age must be positive"
            }
        }
    }
}

data class ReservationTaskAutomationScheduleProperties(
    val enabled: Boolean = false,
    val executionInterval: Duration = Duration.ofMinutes(1),
    val startupDelay: Duration = Duration.ofMinutes(2),
    val batchSize: Int = 10,
    val maxRecordsPerExecution: Int = 10,
    val lockTimeout: Duration = Duration.ofMinutes(5),
    val abandonedLeaseThreshold: Duration = Duration.ofMinutes(10),
    val allowedProfiles: List<String> = emptyList()
) {
    init {
        require(!executionInterval.isNegative && !executionInterval.isZero) {
            "reservation task automation schedule execution interval must be positive"
        }
        require(!startupDelay.isNegative) {
            "reservation task automation schedule startup delay must not be negative"
        }
        require(batchSize in 1..100) {
            "reservation task automation schedule batch size must be between 1 and 100"
        }
        require(maxRecordsPerExecution in 1..1_000) {
            "reservation task automation schedule max records per execution must be between 1 and 1000"
        }
        require(!lockTimeout.isNegative && !lockTimeout.isZero) {
            "reservation task automation schedule lock timeout must be positive"
        }
        require(!abandonedLeaseThreshold.isNegative && !abandonedLeaseThreshold.isZero) {
            "reservation task automation schedule abandoned lease threshold must be positive"
        }
    }
}
