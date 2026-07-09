package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.task.application.TaskLogEntry
import com.hotelopai.task.application.TaskLogRepository
import com.hotelopai.task.application.TaskPage
import com.hotelopai.task.application.TaskPageRequest
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.application.TaskStateHistoryEntry
import com.hotelopai.task.application.TaskStateHistoryRepository
import com.hotelopai.task.domain.Task
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional
class TaskPersistenceRepository(
    private val taskJpaRepository: TaskJpaRepository
) : TaskRepository {
    override fun save(task: Task): Task =
        TaskPersistenceMapper.toDomain(
            taskJpaRepository.saveAndFlush(
                taskJpaRepository.findById(task.id).orElse(null)?.let {
                    TaskPersistenceMapper.updateEntity(it, task)
                } ?: TaskPersistenceMapper.toEntity(task)
            )
        )

    override fun findById(id: UUID): Task? =
        taskJpaRepository.findById(id).orElse(null)?.let(TaskPersistenceMapper::toDomain)

    override fun findAll(): List<Task> =
        taskJpaRepository.findAllByOrderByUpdatedAtDesc().map(TaskPersistenceMapper::toDomain)

    override fun findPage(request: TaskPageRequest): TaskPage<Task> {
        val page = taskJpaRepository.findAll(
            PageRequest.of(
                request.page,
                request.size,
                Sort.by(Sort.Direction.DESC, "updatedAt")
            )
        )

        return TaskPage(
            items = page.content.map(TaskPersistenceMapper::toDomain),
            page = request.page,
            size = request.size,
            totalItems = page.totalElements
        )
    }
}

@Repository
@Transactional
class TaskStateHistoryPersistenceRepository(
    private val taskStateHistoryJpaRepository: TaskStateHistoryJpaRepository
) : TaskStateHistoryRepository {
    override fun append(entry: TaskStateHistoryEntry) {
        taskStateHistoryJpaRepository.saveAndFlush(TaskStateHistoryPersistenceMapper.toEntity(entry))
    }
}

@Repository
@Transactional
class TaskLogPersistenceRepository(
    private val taskLogJpaRepository: TaskLogJpaRepository
) : TaskLogRepository {
    override fun append(entry: TaskLogEntry) {
        taskLogJpaRepository.saveAndFlush(TaskLogPersistenceMapper.toEntity(entry))
    }
}
