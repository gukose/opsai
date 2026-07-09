package com.hotelopai.assistant.domain

data class TaskPreview(
    val type: IntentType,
    val title: String,
    val description: String,
    val roomNumber: String? = null,
    val publicAreaId: String? = null,
    val assetId: String? = null,
    val assignedTeam: String? = null,
    val priority: String? = null,
    val slaMinutes: Int? = null,
    val requiresPmsUpdate: Boolean = false
)
