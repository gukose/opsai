package com.hotelopai.reservation.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.PmsFailureCategory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ObservabilityReservationSyncOperationsAuditSink(
    private val observability: OperationalObservability
) : ReservationSyncOperationsAuditSink {
    override fun record(event: ReservationSyncOperationsAuditEvent) {
        observability.incrementCounter(
            "hotelopai.reservation.sync.audit.total",
            "provider" to event.providerId,
            "operation" to event.action.name.lowercase(),
            "outcome" to event.outcome,
            "failure_category" to (event.failureCategory?.name?.lowercase() ?: "none")
        )
        logger.info(
            "event=reservation_sync_operations action={} provider={} runId={} actorUserId={} outcome={} failureCategory={}",
            event.action,
            event.providerId,
            event.runId ?: "none",
            event.actorUserId ?: "unknown",
            event.outcome,
            event.failureCategory ?: PmsFailureCategory.NONE
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ObservabilityReservationSyncOperationsAuditSink::class.java)
    }
}
