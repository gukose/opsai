package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskAttachmentLinkView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TaskAttachmentLinkService(
    private val taskLifecycleService: TaskLifecycleService,
    private val taskAttachmentLinkRepository: TaskAttachmentLinkRepository
) {
    @Transactional(readOnly = true)
    fun listTaskAttachments(taskId: String, hotelId: UUID): List<TaskAttachmentLinkView> {
        val task = taskLifecycleService.getTaskForHotel(taskId = taskId, hotelId = hotelId)
        return taskAttachmentLinkRepository.findByTaskIdAndHotelId(task.id, hotelId)
    }
}
