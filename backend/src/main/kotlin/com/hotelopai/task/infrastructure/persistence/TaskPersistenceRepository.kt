package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.task.application.TaskLogEntry
import com.hotelopai.task.application.TaskLogRepository
import com.hotelopai.task.application.TaskPage
import com.hotelopai.task.application.TaskPageRequest
import com.hotelopai.task.application.TaskRepository
import com.hotelopai.task.application.TaskAssignmentFilter
import com.hotelopai.task.application.TaskSearchQuery
import com.hotelopai.task.application.TaskStateHistoryEntry
import com.hotelopai.task.application.TaskStateHistoryRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
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

    override fun findByIdAndHotelId(id: UUID, hotelId: UUID): Task? =
        taskJpaRepository.findByIdAndHotelId(id, hotelId)?.let(TaskPersistenceMapper::toDomain)

    override fun findAll(): List<Task> =
        taskJpaRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).map(TaskPersistenceMapper::toDomain)

    override fun findAllByHotelId(hotelId: UUID): List<Task> =
        taskJpaRepository.findAllByHotelIdOrderByUpdatedAtDesc(hotelId).map(TaskPersistenceMapper::toDomain)

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

    override fun findPage(query: TaskSearchQuery): TaskPage<Task> {
        val page = taskJpaRepository.findAll(
            query.toSpecification(),
            PageRequest.of(
                query.pageRequest.page,
                query.pageRequest.size,
                Sort.by(Sort.Direction.DESC, "updatedAt")
            )
        )

        return TaskPage(
            items = page.content.map(TaskPersistenceMapper::toDomain),
            page = query.pageRequest.page,
            size = query.pageRequest.size,
            totalItems = page.totalElements
        )
    }

    private fun TaskSearchQuery.toSpecification(): Specification<TaskJpaEntity> =
        Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()
            predicates += criteriaBuilder.equal(root.get<UUID>("hotelId"), hotelId)

            text?.let { rawText ->
                val pattern = "%${escapeLike(rawText.lowercase())}%"
                predicates += criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("roomNumber")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("assigneeDisplayName")), pattern, '\\')
                )
            }

            if (statuses.isNotEmpty()) {
                predicates += root.get<Any>("status").`in`(statuses)
            }

            if (priorities.isNotEmpty()) {
                predicates += root.get<Any>("priority").`in`(priorities)
            }

            assignment?.let { filter ->
                predicates += when (filter) {
                    TaskAssignmentFilter.ASSIGNED -> criteriaBuilder.isNotNull(root.get<String>("assigneeId"))
                    TaskAssignmentFilter.UNASSIGNED -> criteriaBuilder.isNull(root.get<String>("assigneeId"))
                    TaskAssignmentFilter.MINE -> criteriaBuilder.and(
                        criteriaBuilder.equal(root.get<TaskAssigneeType>("assigneeType"), TaskAssigneeType.USER),
                        criteriaBuilder.equal(root.get<String>("assigneeId"), userId.toString())
                    )
                    TaskAssignmentFilter.ROLE -> if (roleCodes.isEmpty()) {
                        criteriaBuilder.disjunction()
                    } else {
                        criteriaBuilder.and(
                            criteriaBuilder.equal(root.get<TaskAssigneeType>("assigneeType"), TaskAssigneeType.TEAM),
                            root.get<String>("assigneeId").`in`(roleCodes)
                        )
                    }
                    TaskAssignmentFilter.USER -> criteriaBuilder.equal(root.get<TaskAssigneeType>("assigneeType"), TaskAssigneeType.USER)
                    TaskAssignmentFilter.TEAM -> criteriaBuilder.equal(root.get<TaskAssigneeType>("assigneeType"), TaskAssigneeType.TEAM)
                }
            }

            createdFrom?.let {
                predicates += criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), it)
            }
            createdTo?.let {
                predicates += criteriaBuilder.lessThan(root.get("createdAt"), it)
            }

            criteriaBuilder.and(*predicates.toTypedArray())
        }

    private fun escapeLike(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
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
