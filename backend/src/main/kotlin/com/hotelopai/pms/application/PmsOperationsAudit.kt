package com.hotelopai.pms.application

import com.hotelopai.observability.OperationalObservability
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ObservabilityPmsOperationsAuditSink(
    private val observability: OperationalObservability
) : PmsOperationsAuditSink {
    override fun record(event: PmsOperationsAuditEvent) {
        observability.incrementCounter(
            "pms_operations_audit_events_total",
            "provider" to event.providerId,
            "operation" to event.action.name.lowercase(),
            "outcome" to event.outcome,
            "status" to (event.failureCategory?.name?.lowercase() ?: "none")
        )
        logger.info(
            "event=pms_operations_audit action={} provider={} actorUserId={} outcome={} failureCategory={}",
            event.action,
            event.providerId,
            event.actorUserId ?: "unknown",
            event.outcome,
            event.failureCategory ?: PmsFailureCategory.NONE
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ObservabilityPmsOperationsAuditSink::class.java)
    }
}
