package com.hotelopai.task.application

import com.hotelopai.pms.application.PmsProviderRegistry
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.UnsupportedPmsCapabilityException
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsProviderException
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.Instant

@Service
@Profile("!test")
class TaskCompletionPolicy(
    private val pmsProviderRegistry: PmsProviderRegistry
) : CompletionPolicy {
    private val logger = LoggerFactory.getLogger(TaskCompletionPolicy::class.java)
    override fun evaluate(task: Task, now: Instant): CompletionDecision =
        when (task.intentType) {
            // Room readiness is owned by the centralized readiness coordinator.
            // Calling the PMS directly here duplicates the RoomReadyService call
            // performed after housekeeping/inspection state transitions.
            TaskIntentType.HOUSEKEEPING -> CompletionDecision(requiresPmsUpdate = false)

            TaskIntentType.MAINTENANCE -> {
                val roomNumber = extractRoomNumber(task)
                    ?: throw TaskCompletionValidationException(
                        "Maintenance task requires a room number before completion"
                    )

                val result = try {
                    pmsProviderRegistry.activeProviderRequiring(PmsCapability.MAINTENANCE_UPDATE).updateMaintenance(
                        MaintenanceUpdate(
                            roomNumber = roomNumber,
                            issueTypeCode = MAINTENANCE_ISSUE_TYPE_CODE,
                            description = task.description,
                            status = "RESOLVED"
                        )
                    )
                } catch (exception: UnsupportedPmsCapabilityException) {
                    logProviderFailure(task, exception)
                    null
                } catch (exception: PmsProviderException) {
                    logProviderFailure(task, exception)
                    null
                }

                result?.let(::validateVerification) ?: CompletionDecision(requiresPmsUpdate = false)
            }

            else -> CompletionDecision(requiresPmsUpdate = false)
        }

    private fun validateVerification(result: PmsUpdateResult): CompletionDecision {
        require(result.verificationLogId != java.util.UUID(0L, 0L)) {
            "PMS verification log id must not be empty"
        }

        return CompletionDecision(
            requiresPmsUpdate = true,
            verificationLogId = result.verificationLogId
        )
    }

    private fun extractRoomNumber(task: Task): String? =
        task.roomNumber?.trim()?.takeIf { it.isNotBlank() }
            ?: roomPattern.find("${task.title} ${task.description}")
                ?.groupValues
                ?.getOrNull(1)

    private fun logProviderFailure(task: Task, exception: Throwable) {
        var root: Throwable = exception
        while (root.cause != null && root.cause !== root) root = root.cause!!
        logger.warn(
            "event=task_completion_pms_failure taskId={} provider=internal-demo exceptionClass={} rootCauseClass={} rootCauseMessage={}",
            task.id,
            exception::class.simpleName,
            root::class.simpleName,
            root.message.orEmpty().replace(Regex("[\\r\\n]+"), " ").take(200)
        )
    }

    private companion object {
        const val MAINTENANCE_ISSUE_TYPE_CODE = "MAINTENANCE_AC"
        val roomPattern = Regex("""\b(?:room\s*)?(\d{2,5})\b""", RegexOption.IGNORE_CASE)
    }
}
