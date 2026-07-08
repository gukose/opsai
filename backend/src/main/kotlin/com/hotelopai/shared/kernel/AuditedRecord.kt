package com.hotelopai.shared.kernel

import java.time.Instant
import java.util.UUID

interface AuditedRecord {
    val id: UUID
    val version: Long
    val createdAt: Instant
    val createdBy: String?
    val updatedAt: Instant
    val updatedBy: String?
}
