package com.hotelopai.task.application

import com.hotelopai.task.domain.TaskAttachmentLink
import com.hotelopai.task.domain.TaskAttachmentLinkView
import java.util.UUID

interface TaskAttachmentLinkRepository {
    fun saveAll(links: List<TaskAttachmentLink>): List<TaskAttachmentLink>

    fun findByTaskIdAndHotelId(taskId: UUID, hotelId: UUID): List<TaskAttachmentLinkView>
}
