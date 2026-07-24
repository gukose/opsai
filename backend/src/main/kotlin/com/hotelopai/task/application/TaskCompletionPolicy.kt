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
import java.time.Instant

@Service
@Profile("!test")
class TaskCompletionPolicy(
    private val pmsProviderRegistry: PmsProviderRegistry
) : CompletionPolicy {
    override fun evaluate(task: Task, now: Instant): CompletionDecision =
        when (task.intentType) {
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
                    throw TaskCompletionPolicyException("Active PMS provider does not support maintenance updates", exception)
                } catch (exception: PmsProviderException) {
                    throw TaskCompletionPolicyException("UniMock failed during task completion", exception)
                }

                validateVerification(result)
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

    private companion object {
        const val MAINTENANCE_ISSUE_TYPE_CODE = "MAINTENANCE_AC"
        val roomPattern = Regex("""\b(?:room\s*)?(\d{2,5})\b""", RegexOption.IGNORE_CASE)
    }
}
