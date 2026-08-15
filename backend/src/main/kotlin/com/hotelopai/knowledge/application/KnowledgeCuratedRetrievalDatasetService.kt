package com.hotelopai.knowledge.application

import com.hotelopai.knowledge.domain.KnowledgeCategory
import com.hotelopai.knowledge.domain.KnowledgeSource
import org.springframework.stereotype.Service

@Service
class KnowledgeCuratedRetrievalDatasetService(
    private val properties: KnowledgeProperties
) {
    private val sensitivePatterns = listOf(
        Regex("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:\\+?\\d[\\d .-]{7,}\\d)\\b"),
        Regex("\\b(?:api[_-]?key|password|secret|bearer|authorization)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:reservation|guest|property)[_-]?[a-f0-9]{8}\\b", RegexOption.IGNORE_CASE)
    )

    fun dataset(): KnowledgeCuratedEvaluationDataset =
        KnowledgeCuratedEvaluationDataset(
            version = properties.retrievalQuality.datasetVersion,
            documents = documents(),
            cases = cases()
        )

    fun validate(): KnowledgeCuratedDatasetValidationReport {
        val dataset = dataset()
        val failures = mutableListOf<String>()
        val documentRefs = dataset.documents.map { it.reference }
        val caseIds = dataset.cases.map { it.caseId }
        if (documentRefs.distinct().size != documentRefs.size) failures += "duplicate_document_reference"
        if (caseIds.distinct().size != caseIds.size) failures += "duplicate_case_id"
        dataset.documents.forEach { document ->
            if (document.reference.isBlank()) failures += "blank_document_reference"
            if (document.title.isBlank()) failures += "blank_document_title:${document.reference}"
            if (document.content.isBlank()) failures += "blank_document_content:${document.reference}"
            if (!document.language.matches(Regex("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{2,8})?"))) failures += "invalid_document_language:${document.reference}"
            if (containsSensitiveText(document.title) || containsSensitiveText(document.content)) failures += "sensitive_document_content:${document.reference}"
        }
        dataset.cases.sortedBy { it.caseId }.forEach { case ->
            if (case.caseId.isBlank()) failures += "blank_case_id"
            if (case.query.isBlank()) failures += "blank_query:${case.caseId}"
            if (containsSensitiveText(case.query)) failures += "sensitive_query:${case.caseId}"
            if (case.expectedDocumentReferences.isEmpty() && case.expectedChunkReferences.isEmpty()) failures += "missing_expected_reference:${case.caseId}"
            case.expectedDocumentReferences.filterNot { it in documentRefs }.forEach { failures += "missing_document_reference:${case.caseId}:$it" }
            case.relevanceLevels.values.filterNot { it in 1..3 }.forEach { failures += "unsupported_relevance:${case.caseId}:$it" }
            if (case.modes.isEmpty()) failures += "missing_modes:${case.caseId}"
            if (!case.language.matches(Regex("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{2,8})?"))) failures += "invalid_case_language:${case.caseId}"
        }
        return KnowledgeCuratedDatasetValidationReport(dataset.version, failures.isEmpty(), dataset.documents.size, dataset.cases.size, failures.sorted())
    }

    private fun containsSensitiveText(value: String): Boolean =
        sensitivePatterns.any { it.containsMatchIn(value) }

    private fun documents(): List<KnowledgeCuratedDocumentFixture> =
        listOf(
            KnowledgeCuratedDocumentFixture("maintenance-valve-reset", "Synthetic Valve Reset Procedure", KnowledgeCategory.MAINTENANCE, KnowledgeSource.MAINTENANCE_MANUAL, "en", setOf("maintenance", "valve"), "Before resetting a service valve, isolate the panel, verify zero pressure, place a lockout marker, and record the maintenance handoff."),
            KnowledgeCuratedDocumentFixture("housekeeping-room-standard", "Synthetic Room Readiness Standard", KnowledgeCategory.HOUSEKEEPING, KnowledgeSource.SOP, "en", setOf("housekeeping", "room-readiness"), "Room readiness requires clean surfaces, restocked linen, inspected amenities, waste removal, and supervisor release before front desk assignment."),
            KnowledgeCuratedDocumentFixture("frontdesk-checkin-checkout", "Synthetic Check-in Checkout Flow", KnowledgeCategory.FRONT_DESK, KnowledgeSource.OPERATIONS_GUIDE, "en", setOf("front-desk", "arrival", "departure"), "Arrival preparation confirms identity, room readiness, billing readiness, key packet readiness, and checkout follow-up confirms balance closure and luggage assistance."),
            KnowledgeCuratedDocumentFixture("safety-emergency-escalation", "Synthetic Emergency Escalation Instructions", KnowledgeCategory.SAFETY, KnowledgeSource.TRAINING, "en", setOf("safety", "incident"), "For emergency instructions, secure the area, notify the duty manager, contact local emergency services where required, and document the incident category."),
            KnowledgeCuratedDocumentFixture("facilities-hours", "Synthetic Facilities Opening Hours", KnowledgeCategory.GENERAL, KnowledgeSource.OPERATIONS_GUIDE, "en", setOf("facilities", "hours"), "Facilities hours: breakfast service opens at six thirty, fitness room opens at seven, pool access closes at twenty one, and quiet hours start at twenty two."),
            KnowledgeCuratedDocumentFixture("equipment-thermostat", "Synthetic Thermostat Troubleshooting", KnowledgeCategory.MAINTENANCE, KnowledgeSource.MAINTENANCE_MANUAL, "en", setOf("equipment", "thermostat"), "Room thermostat troubleshooting checks power, mode, target temperature, blocked vents, and maintenance escalation if the unit remains unresponsive."),
            KnowledgeCuratedDocumentFixture("guest-service-escalation", "Synthetic Guest Service Recovery", KnowledgeCategory.FRONT_DESK, KnowledgeSource.TRAINING, "en", setOf("guest-service", "escalation"), "Service recovery requires listening, categorizing the concern, offering approved remedies, notifying a supervisor for high-impact issues, and recording the operational outcome."),
            KnowledgeCuratedDocumentFixture("incident-handling", "Synthetic Incident Handling Standard", KnowledgeCategory.SAFETY, KnowledgeSource.SOP, "en", setOf("incident", "escalation"), "Incident handling separates safety risk, maintenance risk, and service disruption, then assigns the responsible department and escalation priority.")
        ).sortedBy { it.reference }

    private fun cases(): List<KnowledgeCuratedEvaluationCase> =
        listOf(
            KnowledgeCuratedEvaluationCase("case-checkout-followup", "checkout follow up balance luggage", setOf("frontdesk-checkin-checkout"), relevanceLevels = mapOf("frontdesk-checkin-checkout" to 3), category = KnowledgeCategory.FRONT_DESK),
            KnowledgeCuratedEvaluationCase("case-emergency-duty-manager", "emergency duty manager incident instructions", setOf("safety-emergency-escalation", "incident-handling"), relevanceLevels = mapOf("safety-emergency-escalation" to 3, "incident-handling" to 2), category = KnowledgeCategory.SAFETY),
            KnowledgeCuratedEvaluationCase("case-facility-quiet-hours", "pool closes quiet hours fitness room", setOf("facilities-hours"), relevanceLevels = mapOf("facilities-hours" to 3), category = KnowledgeCategory.GENERAL),
            KnowledgeCuratedEvaluationCase("case-housekeeping-ready-room", "room readiness restocked linen supervisor release", setOf("housekeeping-room-standard"), relevanceLevels = mapOf("housekeeping-room-standard" to 3), category = KnowledgeCategory.HOUSEKEEPING),
            KnowledgeCuratedEvaluationCase("case-service-recovery", "service recovery supervisor approved remedy", setOf("guest-service-escalation"), relevanceLevels = mapOf("guest-service-escalation" to 3), category = KnowledgeCategory.FRONT_DESK),
            KnowledgeCuratedEvaluationCase("case-thermostat-unresponsive", "thermostat unresponsive blocked vents target temperature", setOf("equipment-thermostat"), relevanceLevels = mapOf("equipment-thermostat" to 3), category = KnowledgeCategory.MAINTENANCE),
            KnowledgeCuratedEvaluationCase("case-valve-lockout", "valve pressure lockout maintenance handoff", setOf("maintenance-valve-reset"), relevanceLevels = mapOf("maintenance-valve-reset" to 3), category = KnowledgeCategory.MAINTENANCE)
        ).sortedBy { it.caseId }
}
