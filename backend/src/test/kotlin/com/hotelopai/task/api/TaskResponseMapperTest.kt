package com.hotelopai.task.api

import com.hotelopai.task.application.TaskPage
import com.hotelopai.task.application.TaskStateHistoryEntry
import com.hotelopai.task.application.TaskStateHistoryRepository
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class TaskResponseMapperTest {
    private val hotelId=UUID.randomUUID()

    @Test fun `detail response uses persisted history timing`() {
        val repository=mock(TaskStateHistoryRepository::class.java)
        val task=completedTask("Detail")
        `when`(repository.findByTaskId(task.id)).thenReturn(history(task.id,
            event("09:00",TaskStatus.STARTED,TaskTransition.START),
            event("09:20",TaskStatus.WAITING,TaskTransition.PAUSE),
            event("09:30",TaskStatus.IN_PROGRESS,TaskTransition.RESUME),
            event("09:50",TaskStatus.COMPLETED,TaskTransition.COMPLETE)))

        val response=TaskResponseMapper(repository).toResponse(task)

        assertThat(response.totalPauseDurationSeconds).isEqualTo(600)
        assertThat(response.actualWorkingDurationSeconds).isEqualTo(2400)
        verify(repository,times(1)).findByTaskId(task.id)
    }

    @Test fun `list loads all histories once without per-task lookups and maps every timing`() {
        val repository=mock(TaskStateHistoryRepository::class.java)
        val tasks=listOf(completedTask("One"),completedTask("Two"),completedTask("Three"))
        val histories=tasks.mapIndexed { index,task ->
            task.id to history(task.id,
                event("09:00",TaskStatus.STARTED,TaskTransition.START),
                event("09:0${index+1}",TaskStatus.WAITING,TaskTransition.PAUSE),
                event("09:10",TaskStatus.IN_PROGRESS,TaskTransition.RESUME),
                event("09:20",TaskStatus.COMPLETED,TaskTransition.COMPLETE))
        }.toMap()
        `when`(repository.findByTaskIds(tasks.map { it.id })).thenReturn(histories)

        val response=TaskResponseMapper(repository).toPageResponse(TaskPage(tasks,0,20,3))

        verify(repository,times(1)).findByTaskIds(tasks.map { it.id })
        tasks.forEach { verify(repository,never()).findByTaskId(it.id) }
        assertThat(response.items.map { it.totalPauseDurationSeconds }).containsExactly(540,480,420)
        assertThat(response.items.map { it.actualWorkingDurationSeconds }).containsExactly(660,720,780)
    }

    @Test fun `multiple persisted pauses appear in API response timing`() {
        val repository=mock(TaskStateHistoryRepository::class.java)
        val task=completedTask("Multiple pauses")
        `when`(repository.findByTaskId(task.id)).thenReturn(history(task.id,
            event("09:00",TaskStatus.STARTED,TaskTransition.START),
            event("09:10",TaskStatus.WAITING,TaskTransition.PAUSE),
            event("09:15",TaskStatus.IN_PROGRESS,TaskTransition.RESUME),
            event("09:25",TaskStatus.WAITING,TaskTransition.PAUSE),
            event("09:35",TaskStatus.IN_PROGRESS,TaskTransition.RESUME),
            event("09:50",TaskStatus.COMPLETED,TaskTransition.COMPLETE)))

        val response=TaskResponseMapper(repository).toResponse(task)

        assertThat(response.totalPauseDurationSeconds).isEqualTo(900)
        assertThat(response.actualWorkingDurationSeconds).isEqualTo(2100)
    }

    @Test fun `completed task reload maps identical persisted timing`() {
        val repository=mock(TaskStateHistoryRepository::class.java)
        val task=completedTask("Reload")
        val persisted=history(task.id,
            event("09:00",TaskStatus.STARTED,TaskTransition.START),
            event("09:20",TaskStatus.WAITING,TaskTransition.PAUSE),
            event("09:30",TaskStatus.IN_PROGRESS,TaskTransition.RESUME),
            event("09:50",TaskStatus.COMPLETED,TaskTransition.COMPLETE))
        `when`(repository.findByTaskId(task.id)).thenReturn(persisted)
        val mapper=TaskResponseMapper(repository)

        val first=mapper.toResponse(task)
        val reloaded=mapper.toResponse(task.copy())

        assertThat(reloaded.totalPauseDurationSeconds).isEqualTo(first.totalPauseDurationSeconds)
        assertThat(reloaded.actualWorkingDurationSeconds).isEqualTo(first.actualWorkingDurationSeconds)
        verify(repository,times(2)).findByTaskId(task.id)
    }

    @Test fun `TaskResponse has no timing fallback factory`() {
        val factoryMethods=TaskResponse.Companion::class.java.declaredMethods.filter { it.name=="from" }
        assertThat(factoryMethods).allMatch { it.parameterCount==2 }
    }

    private fun completedTask(title:String):Task {
        val created=Instant.parse("2026-01-01T08:00:00Z")
        return Task.create(hotelId,TaskIntentType.GENERAL_OPERATIONAL_NOTE,TaskSource.MANUAL,title,"Timing test",priority=TaskPriority.MEDIUM,slaDeadline=Instant.parse("2026-01-02T08:00:00Z"),createdAt=created)
            .copy(status=TaskStatus.COMPLETED,startedAt=Instant.parse("2026-01-01T09:00:00Z"),completedAt=Instant.parse("2026-01-01T09:50:00Z"),updatedAt=Instant.parse("2026-01-01T09:50:00Z"))
    }

    private fun event(time:String,status:TaskStatus,transition:TaskTransition)=Triple(time,status,transition)
    private fun history(taskId:UUID,vararg events:Triple<String,TaskStatus,TaskTransition>)=events.map { (time,status,transition) ->
        TaskStateHistoryEntry(taskId,hotelId,null,status,transition,occurredAt=Instant.parse("2026-01-01T${time}:00Z"))
    }
}
