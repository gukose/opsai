package com.hotelopai.task.application

import com.hotelopai.shared.pms.MaintenanceCompletionPort
import com.hotelopai.shared.pms.MaintenanceCompletionRequest
import com.hotelopai.shared.pms.PmsCompletionException
import com.hotelopai.shared.pms.MaintenanceCompletionResult
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@Profile("!test")
class TaskCompletionPolicy(
    private val maintenanceCompletionPort: MaintenanceCompletionPort
) : CompletionPolicy {
    override fun evaluate(task: Task, now: Instant): CompletionDecision =
        when (task.intentType) {
            TaskIntentType.MAINTENANCE -> {
                val roomNumber = extractRoomNumber(task)
                    ?: throw TaskCompletionValidationException(
                        "Maintenance task requires a room number before completion"
                    )

                val result = try {
                    maintenanceCompletionPort.updateMaintenance(
                        MaintenanceCompletionRequest(
                            roomNumber = roomNumber,
                            issueTypeCode = MAINTENANCE_ISSUE_TYPE_CODE,
                            description = task.description,
                            status = "RESOLVED"
                        )
                    )
                } catch (exception: PmsCompletionException) {
                    throw TaskCompletionPolicyException("UniMock failed during task completion", exception)
                }

                validateVerification(result)
            }

            else -> CompletionDecision(requiresPmsUpdate = false)
        }

    private fun validateVerification(result: MaintenanceCompletionResult): CompletionDecision {
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

    private companion object {
        const val MAINTENANCE_ISSUE_TYPE_CODE = "MAINTENANCE_AC"
        val roomPattern = Regex("""\b(?:room\s*)?(\d{2,5})\b""", RegexOption.IGNORE_CASE)
    }
}
