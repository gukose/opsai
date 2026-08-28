package com.hotelopai.integration.unimock.demo

import com.hotelopai.housekeeping.application.CreateHousekeepingCommand
import com.hotelopai.housekeeping.application.HousekeepingService
import com.hotelopai.housekeeping.application.MinibarReadinessService
import com.hotelopai.housekeeping.domain.HousekeepingStatus
import com.hotelopai.housekeeping.domain.HousekeepingWorkflow
import com.hotelopai.housekeeping.domain.HousekeepingWorkflowType
import com.hotelopai.task.application.*
import com.hotelopai.task.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class Function15BTest {
    private val hotelId=UUID.randomUUID()
    private val now=Instant.parse("2026-08-28T10:00:00Z")
    private val clock=Clock.fixed(now,ZoneOffset.UTC)

    @Test fun `checkout creates departure workflow one PMS minibar task and pending readiness`() {
        val housekeeping=mock(HousekeepingService::class.java)
        val lifecycle=mock(TaskLifecycleService::class.java)
        val readiness=mock(MinibarReadinessService::class.java)
        val workflow=workflow()
        val minibar=task(TaskIntentType.MINIBAR,TaskSource.PMS)
        val housekeepingCaptor=ArgumentCaptor.forClass(CreateHousekeepingCommand::class.java)
        val taskCaptor=ArgumentCaptor.forClass(CreateTaskCommand::class.java)
        doReturn(workflow).`when`(housekeeping).create(org.mockito.ArgumentMatchers.any(CreateHousekeepingCommand::class.java) ?: checkoutCommand())
        doReturn(minibar).`when`(lifecycle).createTask(org.mockito.ArgumentMatchers.any(CreateTaskCommand::class.java) ?: minibarCommand())

        val result=PmsCheckoutOrchestrator(housekeeping,lifecycle,readiness).checkout(hotelId,"318","provider-123",now)

        verify(housekeeping,times(1)).create(housekeepingCaptor.capture() ?: checkoutCommand())
        verify(lifecycle,times(1)).createTask(taskCaptor.capture() ?: minibarCommand())
        verify(readiness,times(1)).markPending(hotelId,"318")
        assertThat(result.workflow).isEqualTo(workflow)
        assertThat(housekeepingCaptor.value.type).isEqualTo(HousekeepingWorkflowType.DEPARTURE_CLEANING)
        assertThat(housekeepingCaptor.value.idempotencyKey).isEqualTo("pms:provider-123")
        assertThat(taskCaptor.value.intentType).isEqualTo(TaskIntentType.MINIBAR)
        assertThat(taskCaptor.value.source).isEqualTo(TaskSource.PMS)
        assertThat(taskCaptor.value.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(taskCaptor.value.hotelId).isEqualTo(hotelId)
        assertThat(taskCaptor.value.roomNumber).isEqualTo("318")
        assertThat(taskCaptor.value.description).contains("providerEventId=provider-123")
    }

    @Test fun `minibar creation runs existing assignment orchestration exactly once`() {
        val repository=MemoryTaskRepository()
        val assignment=mock(TaskCreationAssignmentOrchestrator::class.java)
        doReturn(AutomaticAssignmentResult(TaskAssignment(TaskAssigneeType.USER,UUID.randomUUID().toString(),"Minibar employee",now),"AUTO_ASSIGNED"))
            .`when`(assignment).evaluate(org.mockito.ArgumentMatchers.any(Task::class.java) ?: task(),org.mockito.ArgumentMatchers.any(Instant::class.java) ?: now)
        val lifecycle=lifecycle(repository,assignment=assignment)

        val created=lifecycle.createTask(minibarCommand())

        assertThat(created.assignment).isNotNull
        verify(assignment,times(1)).evaluate(org.mockito.ArgumentMatchers.any(Task::class.java) ?: created,org.mockito.ArgumentMatchers.any(Instant::class.java) ?: now)
    }

    @Test fun `normal completion marks minibar once and leaves non minibar unchanged`() {
        val repository=MemoryTaskRepository()
        val readiness=mock(MinibarReadinessService::class.java)
        val lifecycle=lifecycle(repository,readiness=readiness)
        val minibar=lifecycle.createTask(minibarCommand().copy(assignment=AssignmentCommand(TaskAssigneeType.USER,UUID.randomUUID().toString(),"Employee")))
        lifecycle.startTask(minibar.id.toString(),hotelId,now)

        lifecycle.completeTask(minibar.id.toString(),hotelId,now.plusSeconds(60))
        lifecycle.completeTask(minibar.id.toString(),hotelId,now.plusSeconds(120))

        verify(readiness,times(1)).markCompleted(hotelId,"318")
        val normal=lifecycle.createTask(minibarCommand().copy(intentType=TaskIntentType.GUEST_REQUEST,roomNumber="205",assignment=AssignmentCommand(TaskAssigneeType.USER,UUID.randomUUID().toString(),"Employee")))
        lifecycle.startTask(normal.id.toString(),hotelId,now)
        lifecycle.completeTask(normal.id.toString(),hotelId,now.plusSeconds(60))
        verify(readiness,never()).markCompleted(hotelId,"205")
    }

    @Test fun `cleaning first remains blocked then minibar completion marks ready`() {
        val jdbc=mockJdbc(1L,1L,1L,0L)
        val readiness=MinibarReadinessService(jdbc,clock)
        assertThat(readiness.reevaluateRoomReadiness(hotelId,"318")).isFalse()
        assertThat(readiness.markCompleted(hotelId,"318")).isTrue()
        verify(jdbc,times(1)).update(org.mockito.ArgumentMatchers.contains("room_operational_state"),anyMap<String,Any>() ?: emptyMap<String,Any>())
    }

    @Test fun `minibar first remains blocked then cleaning completion marks ready`() {
        val jdbc=mockJdbc(0L,0L,1L,0L)
        val readiness=MinibarReadinessService(jdbc,clock)
        assertThat(readiness.markCompleted(hotelId,"318")).isFalse()
        assertThat(readiness.reevaluateRoomReadiness(hotelId,"318")).isTrue()
        verify(jdbc,times(1)).update(org.mockito.ArgumentMatchers.contains("room_operational_state"),anyMap<String,Any>() ?: emptyMap<String,Any>())
    }

    private fun mockJdbc(vararg counts:Long):NamedParameterJdbcTemplate {
        val jdbc=mock(NamedParameterJdbcTemplate::class.java)
        doReturn(1).`when`(jdbc).update(org.mockito.ArgumentMatchers.anyString(),anyMap<String,Any>() ?: emptyMap<String,Any>())
        val ongoing=doReturn(counts.first(),*counts.drop(1).toTypedArray()).`when`(jdbc)
        ongoing.queryForObject(org.mockito.ArgumentMatchers.anyString(),anyMap<String,Any>() ?: emptyMap<String,Any>(),org.mockito.ArgumentMatchers.eq(Long::class.java) ?: Long::class.java)
        return jdbc
    }

    private fun lifecycle(repository:TaskRepository,assignment:TaskCreationAssignmentOrchestrator=NoOpTaskCreationAssignmentOrchestrator,readiness:MinibarReadinessService?=null)=TaskLifecycleService(
        taskRepository=repository,
        completionPolicy=NoOpCompletionPolicy(),
        clock=clock,
        taskCreationAssignmentOrchestrator=assignment,
        minibarReadinessService=readiness
    )
    private fun minibarCommand()=CreateTaskCommand(hotelId,TaskIntentType.MINIBAR,TaskSource.PMS,"Minibar Check","providerEventId=provider-123","318",TaskPriority.HIGH,now.plusSeconds(7200))
    private fun checkoutCommand()=CreateHousekeepingCommand(hotelId,"318",HousekeepingWorkflowType.DEPARTURE_CLEANING,true,"pms:provider-123")
    private fun workflow()=HousekeepingWorkflow(UUID.randomUUID(),hotelId,UUID.randomUUID(),HousekeepingWorkflowType.DEPARTURE_CLEANING,"318",HousekeepingStatus.CREATED,true,idempotencyKey="pms:provider-123",createdAt=now,updatedAt=now)
    private fun task(intent:TaskIntentType=TaskIntentType.MINIBAR,source:TaskSource=TaskSource.PMS)=Task.create(hotelId,intent,source,"Task","Description",priority=TaskPriority.HIGH,slaDeadline=now.plusSeconds(7200),createdAt=now)
}

private class MemoryTaskRepository:TaskRepository {
    private val tasks=linkedMapOf<UUID,Task>()
    override fun save(task:Task)=task.also { tasks[it.id]=it }
    override fun findById(id:UUID)=tasks[id]
    override fun findAll()=tasks.values.toList()
    override fun findPage(request:TaskPageRequest)=TaskPage(findAll(),request.page,request.size,tasks.size.toLong())
}
