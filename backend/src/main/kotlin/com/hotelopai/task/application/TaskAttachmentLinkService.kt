package com.hotelopai.task.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.task.domain.TaskAttachmentLinkView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TaskAttachmentLinkService(
    private val taskLifecycleService: TaskLifecycleService,
    private val taskAttachmentLinkRepository: TaskAttachmentLinkRepository,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional(readOnly = true)
    fun listTaskAttachments(taskId: String, hotelId: UUID): List<TaskAttachmentLinkView> {
        try {
            val task = taskLifecycleService.getTaskForHotel(taskId = taskId, hotelId = hotelId)
            return taskAttachmentLinkRepository.findByTaskIdAndHotelId(task.id, hotelId).also {
                recordRead("success", "none")
            }
        } catch (exception: TaskNotFoundException) {
            recordRead("not_found", "task_not_found")
            throw exception
        } catch (exception: RuntimeException) {
            recordRead("failure", "operation_failed")
            throw exception
        }
    }

    private fun recordRead(outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.task.attachment.read.total",
            "operation" to "read",
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }
}
