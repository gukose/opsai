package com.hotelopai.task.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TaskJpaRepository : JpaRepository<TaskJpaEntity, UUID> {
    fun findAllByOrderByUpdatedAtDesc(): List<TaskJpaEntity>
}

interface TaskStateHistoryJpaRepository : JpaRepository<TaskStateHistoryJpaEntity, UUID> {
    fun findAllByTaskIdOrderByCreatedAtAsc(taskId: UUID): List<TaskStateHistoryJpaEntity>

    fun countByTaskId(taskId: UUID): Long
}

interface TaskLogJpaRepository : JpaRepository<TaskLogJpaEntity, UUID> {
    fun findAllByTaskIdOrderByCreatedAtAsc(taskId: UUID): List<TaskLogJpaEntity>

    fun countByTaskId(taskId: UUID): Long
}
