package com.hotelopai.task.application

import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskVisibilityTest {
    private val hotel = UUID.randomUUID()
    private val yasemin = UUID.randomUUID()
    private val zeynep = UUID.randomUUID()
    private val yaseminUser = UUID.randomUUID()

    @Test
    fun `normal employee sees only canonical assigned tasks`() {
        val scope = TaskVisibilityScope(hotel, TaskVisibilityLevel.SELF, yasemin, yaseminUser, setOf("HOUSEKEEPER"))
        assertThat(TaskVisibilityRules.canView(task(TaskIntentType.HOUSEKEEPING, yasemin.toString()), scope)).isTrue()
        assertThat(TaskVisibilityRules.canView(task(TaskIntentType.HOUSEKEEPING, zeynep.toString()), scope)).isFalse()
        assertThat(TaskVisibilityRules.canView(task(TaskIntentType.HOUSEKEEPING, null), scope)).isFalse()
    }

    @Test
    fun `housekeeping supervisor sees housekeeping but not maintenance`() {
        val scope = TaskVisibilityScope(
            hotel,
            TaskVisibilityLevel.DEPARTMENT,
            null,
            UUID.randomUUID(),
            setOf("HOUSEKEEPING_SUPERVISOR")
        )
        assertThat(TaskVisibilityRules.canView(task(TaskIntentType.HOUSEKEEPING, null), scope)).isTrue()
        assertThat(TaskVisibilityRules.canView(task(TaskIntentType.MAINTENANCE, null), scope)).isFalse()
    }

    @Test
    fun `hotel admin sees every task in own hotel only`() {
        val scope = TaskVisibilityScope(hotel, TaskVisibilityLevel.HOTEL, null, UUID.randomUUID(), setOf("ADMIN"))
        assertThat(TaskVisibilityRules.canView(task(TaskIntentType.MAINTENANCE, null), scope)).isTrue()
        assertThat(TaskVisibilityRules.canView(task(TaskIntentType.MAINTENANCE, null, UUID.randomUUID()), scope)).isFalse()
    }

    private fun task(intent: TaskIntentType, assignee: String?, taskHotel: UUID = hotel): Task =
        Task.create(
            hotelId = taskHotel,
            intentType = intent,
            source = TaskSource.MANUAL,
            title = "Visibility test",
            description = "Visibility test",
            priority = TaskPriority.MEDIUM,
            slaDeadline = Instant.now().plusSeconds(3600)
        ).let { created ->
            assignee?.let {
                created.assign(
                    com.hotelopai.task.domain.TaskAssignment(TaskAssigneeType.USER, it, "Employee", Instant.now())
                )
            } ?: created
        }
}
