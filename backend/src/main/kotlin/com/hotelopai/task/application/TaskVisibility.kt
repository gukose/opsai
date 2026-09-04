package com.hotelopai.task.application

import com.hotelopai.shared.security.CurrentUserContext
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import java.util.UUID

/** The server-side scope used for every task read, including direct detail reads. */
enum class TaskVisibilityLevel { SELF, DEPARTMENT, HOTEL }

data class TaskVisibilityScope(
    val hotelId: UUID,
    val level: TaskVisibilityLevel,
    val employeeId: UUID?,
    val userId: UUID,
    val roleCodes: Set<String>,
    val canonicalEmployeeUserId: UUID? = null
) {
    companion object {
        fun from(current: CurrentUserContext): TaskVisibilityScope {
            val roles = current.roles.map(String::uppercase).toSet()
            val hotelWide = roles.any { it in setOf("ADMIN", "GM", "GENERAL_MANAGER", "HOTEL_ADMIN") }
            val scoped = roles.any { it.contains("SUPERVISOR") || it.contains("MANAGER") || it == "CHIEF_ENGINEER" }
            return TaskVisibilityScope(
                level = when {
                    hotelWide -> TaskVisibilityLevel.HOTEL
                    scoped -> TaskVisibilityLevel.DEPARTMENT
                    else -> TaskVisibilityLevel.SELF
                },
                hotelId = current.hotelId,
                employeeId = current.employeeId,
                userId = current.userId,
                roleCodes = roles,
                canonicalEmployeeUserId = current.canonicalEmployeeUserId
            )
        }
    }
}

object TaskVisibilityRules {
    private val housekeepingIntents = setOf(
        TaskIntentType.HOUSEKEEPING,
        TaskIntentType.MINIBAR,
        TaskIntentType.FLASH_TASK,
        TaskIntentType.LAUNDRY,
        TaskIntentType.TRAY_REMOVAL,
        TaskIntentType.PUBLIC_AREA
    )
    private val technicalIntents = setOf(TaskIntentType.MAINTENANCE, TaskIntentType.DAMAGE_REPORT)
    private val frontOfficeIntents = setOf(TaskIntentType.GUEST_REQUEST, TaskIntentType.LOST_AND_FOUND)
    private val securityIntents = emptySet<TaskIntentType>()
    // Generic operational notes are intentionally supervisor-visible so the
    // fallback assistant flow can be routed for assignment instead of becoming
    // invisible when no specialised intent was recognised.
    private val supervisorFallbackIntents = setOf(TaskIntentType.GENERAL_OPERATIONAL_NOTE)

    fun canView(task: Task, scope: TaskVisibilityScope): Boolean {
        if (task.hotelId != scope.hotelId) return false
        return when (scope.level) {
            TaskVisibilityLevel.HOTEL -> true
            TaskVisibilityLevel.SELF -> {
                task.assignment?.assigneeType == TaskAssigneeType.USER &&
                    task.assignment.assigneeId in setOfNotNull(
                        scope.employeeId,
                        scope.userId,
                        scope.canonicalEmployeeUserId
                    ).map(UUID::toString).toSet()
            }
            TaskVisibilityLevel.DEPARTMENT -> task.intentType in allowedIntents(scope.roleCodes)
        }
    }

    fun allowedIntents(roleCodes: Set<String>): Set<TaskIntentType> = when {
        roleCodes.any { it.contains("HOUSEKEEPING") } -> housekeepingIntents + supervisorFallbackIntents
        roleCodes.any { it.contains("TECHNICAL") || it.contains("ENGINEER") || it.contains("MAINTENANCE") } -> technicalIntents + supervisorFallbackIntents
        roleCodes.any { it.contains("FRONT_OFFICE") || it == "RECEPTION_SUPERVISOR" || it == "RECEPTION_MANAGER" } -> frontOfficeIntents + supervisorFallbackIntents
        roleCodes.any { it.contains("SECURITY") } -> securityIntents + supervisorFallbackIntents
        else -> emptySet()
    }

    fun isBroad(scope: TaskVisibilityScope): Boolean = scope.level != TaskVisibilityLevel.SELF
}
