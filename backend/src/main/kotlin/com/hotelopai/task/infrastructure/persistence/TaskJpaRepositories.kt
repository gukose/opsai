package com.hotelopai.task.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import com.hotelopai.task.domain.TaskStatus
import java.util.UUID

interface TaskJpaRepository : JpaRepository<TaskJpaEntity, UUID>, JpaSpecificationExecutor<TaskJpaEntity> {
    fun findAllByHotelIdOrderByUpdatedAtDesc(hotelId: UUID): List<TaskJpaEntity>

    fun findByIdAndHotelId(id: UUID, hotelId: UUID): TaskJpaEntity?

    @Query("select t from TaskJpaEntity t where t.hotelId=:hotelId and t.id<>:excludingTaskId and t.assigneeId in :assigneeIds and t.status in :statuses order by t.updatedAt desc")
    fun findActiveAssignedTask(
        @Param("hotelId") hotelId: UUID,
        @Param("assigneeIds") assigneeIds: Set<String>,
        @Param("excludingTaskId") excludingTaskId: UUID,
        @Param("statuses") statuses: Set<TaskStatus>
    ): List<TaskJpaEntity>
}

interface TaskStateHistoryJpaRepository : JpaRepository<TaskStateHistoryJpaEntity, UUID> {
    fun findAllByTaskIdOrderByCreatedAtAsc(taskId: UUID): List<TaskStateHistoryJpaEntity>
    fun findAllByTaskIdInOrderByTaskIdAscCreatedAtAsc(taskIds: Collection<UUID>): List<TaskStateHistoryJpaEntity>

    fun countByTaskId(taskId: UUID): Long
}

interface TaskLogJpaRepository : JpaRepository<TaskLogJpaEntity, UUID> {
    fun findAllByTaskIdOrderByCreatedAtAsc(taskId: UUID): List<TaskLogJpaEntity>

    fun countByTaskId(taskId: UUID): Long
}
