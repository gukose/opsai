package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.TaskCreationCandidate
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import java.time.Instant
import java.util.UUID

fun TaskCreationCandidate.toCreateTaskCommand(
    hotelId: String,
    now: Instant
): CreateTaskCommand =
    CreateTaskCommand(
        hotelId = UUID.fromString(hotelId),
        intentType = preview.type.toTaskIntentType(),
        source = TaskSource.ASSISTANT,
        title = preview.title,
        description = preview.description,
        priority = preview.priority.toTaskPriority(),
        slaDeadline = now.plusSeconds((preview.slaMinutes ?: 60).toLong() * 60)
    )

fun IntentType.toTaskIntentType(): TaskIntentType =
    when (this) {
        IntentType.GUEST_REQUEST -> TaskIntentType.GUEST_REQUEST
        IntentType.MAINTENANCE -> TaskIntentType.MAINTENANCE
        IntentType.HOUSEKEEPING -> TaskIntentType.HOUSEKEEPING
        IntentType.DAMAGE_REPORT -> TaskIntentType.DAMAGE_REPORT
        IntentType.LOST_AND_FOUND -> TaskIntentType.LOST_AND_FOUND
        IntentType.TRAY_REMOVAL -> TaskIntentType.TRAY_REMOVAL
        IntentType.LAUNDRY -> TaskIntentType.LAUNDRY
        IntentType.MINIBAR -> TaskIntentType.MINIBAR
        IntentType.FLASH_TASK -> TaskIntentType.FLASH_TASK
        IntentType.SHIFT_HANDOVER -> TaskIntentType.SHIFT_HANDOVER
        IntentType.PUBLIC_AREA -> TaskIntentType.PUBLIC_AREA
        IntentType.INVENTORY -> TaskIntentType.INVENTORY
        IntentType.DELIVERIES -> TaskIntentType.DELIVERIES
        IntentType.UNKNOWN -> throw IllegalArgumentException("Cannot create task for unknown intent")
    }

private fun String?.toTaskPriority(): TaskPriority =
    when (this?.trim()?.uppercase()) {
        "LOW" -> TaskPriority.LOW
        "MEDIUM" -> TaskPriority.MEDIUM
        "HIGH" -> TaskPriority.HIGH
        "URGENT" -> TaskPriority.URGENT
        null, "" -> TaskPriority.MEDIUM
        else -> throw IllegalArgumentException("Unknown task priority: $this")
    }
