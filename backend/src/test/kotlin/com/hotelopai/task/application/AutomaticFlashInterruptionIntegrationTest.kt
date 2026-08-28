package com.hotelopai.task.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.task.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.UUID

class AutomaticFlashInterruptionIntegrationTest {
    private val hotelId=UUID.randomUUID()
    private val employeeId=UUID.randomUUID()
    private val now=Instant.parse("2026-08-28T10:00:00Z")

    @Test fun `automatic high minibar assignment pauses started task and normal completion resumes it exactly once`() {
        val fixture=fixture(employeeId)
        val normal=fixture.lifecycle.createTask(command(TaskIntentType.HOUSEKEEPING,TaskPriority.MEDIUM,AssignmentCommand(TaskAssigneeType.USER,employeeId.toString(),"Employee")))
        fixture.lifecycle.startTask(normal.id.toString(),hotelId,now.plusSeconds(1))
        assertThat(fixture.repository.findById(normal.id)!!.status).isEqualTo(TaskStatus.STARTED)

        val minibar=fixture.lifecycle.createTask(command(TaskIntentType.MINIBAR,TaskPriority.HIGH,null),now.plusSeconds(2))

        assertThat(minibar.assignment?.assigneeId).isEqualTo(employeeId.toString())
        assertThat(fixture.repository.findById(normal.id)!!.status).isEqualTo(TaskStatus.WAITING)
        assertThat(fixture.store.records).hasSize(1)
        val interruption=fixture.store.records.single()
        assertThat(interruption.pausedTaskId).isEqualTo(normal.id)
        assertThat(interruption.interruptingTaskId).isEqualTo(minibar.id)
        assertThat(interruption.source).isEqualTo(InterruptionSource.FLASH_INTERRUPTION)

        fixture.lifecycle.completeTask(minibar.id.toString(),hotelId,now.plusSeconds(3))

        assertThat(fixture.repository.findById(normal.id)!!.status).isEqualTo(TaskStatus.IN_PROGRESS)
        assertThat(fixture.store.records).singleElement().extracting(TaskInterruptionRecord::status).isEqualTo("RESUMED")
    }

    @Test fun `different employee and no active task do not interrupt unrelated work`() {
        val otherEmployee=UUID.randomUUID()
        val fixture=fixture(otherEmployee)
        val normal=fixture.lifecycle.createTask(command(TaskIntentType.HOUSEKEEPING,TaskPriority.MEDIUM,AssignmentCommand(TaskAssigneeType.USER,employeeId.toString(),"Other")))
        fixture.lifecycle.startTask(normal.id.toString(),hotelId,now.plusSeconds(1))

        fixture.lifecycle.createTask(command(TaskIntentType.MINIBAR,TaskPriority.HIGH,null),now.plusSeconds(2))

        assertThat(fixture.repository.findById(normal.id)!!.status).isEqualTo(TaskStatus.STARTED)
        assertThat(fixture.store.records).isEmpty()
        val noActive=fixture(otherEmployee)
        noActive.lifecycle.createTask(command(TaskIntentType.MINIBAR,TaskPriority.HIGH,null),now.plusSeconds(2))
        assertThat(noActive.store.records).isEmpty()
    }

    @Test fun `completed and cancelled tasks are not active interruption candidates`() {
        listOf(TaskStatus.COMPLETED,TaskStatus.CANCELLED).forEach { terminal ->
            val fixture=fixture(employeeId)
            val task=fixture.lifecycle.createTask(command(TaskIntentType.HOUSEKEEPING,TaskPriority.MEDIUM,AssignmentCommand(TaskAssigneeType.USER,employeeId.toString(),"Employee")))
            fixture.lifecycle.startTask(task.id.toString(),hotelId,now.plusSeconds(1))
            if(terminal==TaskStatus.COMPLETED) fixture.lifecycle.completeTask(task.id.toString(),hotelId,now.plusSeconds(2)) else fixture.lifecycle.cancelTask(task.id.toString(),hotelId,now.plusSeconds(2))
            fixture.lifecycle.createTask(command(TaskIntentType.MINIBAR,TaskPriority.HIGH,null),now.plusSeconds(3))
            assertThat(fixture.store.records).isEmpty()
        }
    }

    @Test fun `overlapping automatic flash tasks resume in persisted interruption order`() {
        val fixture=fixture(employeeId)
        val normal=fixture.lifecycle.createTask(command(TaskIntentType.HOUSEKEEPING,TaskPriority.MEDIUM,AssignmentCommand(TaskAssigneeType.USER,employeeId.toString(),"Employee")))
        fixture.lifecycle.startTask(normal.id.toString(),hotelId,now.plusSeconds(1))
        fixture.clock.current=now.plusSeconds(2)
        val first=fixture.lifecycle.createTask(command(TaskIntentType.MINIBAR,TaskPriority.HIGH,null),now.plusSeconds(2))
        fixture.clock.current=now.plusSeconds(3)
        val second=fixture.lifecycle.createTask(command(TaskIntentType.FLASH_TASK,TaskPriority.HIGH,null),now.plusSeconds(3))
        assertThat(fixture.store.records).hasSize(2)
        assertThat(fixture.repository.findById(normal.id)!!.status).isEqualTo(TaskStatus.WAITING)
        assertThat(fixture.repository.findById(first.id)!!.status).isEqualTo(TaskStatus.WAITING)

        fixture.clock.current=now.plusSeconds(4)
        fixture.lifecycle.completeTask(second.id.toString(),hotelId,now.plusSeconds(4))
        assertThat(fixture.repository.findById(first.id)!!.status).isEqualTo(TaskStatus.IN_PROGRESS)
        assertThat(fixture.repository.findById(normal.id)!!.status).isEqualTo(TaskStatus.WAITING)
        fixture.clock.current=now.plusSeconds(5)
        fixture.lifecycle.completeTask(first.id.toString(),hotelId,now.plusSeconds(5))
        assertThat(fixture.repository.findById(normal.id)!!.status).isEqualTo(TaskStatus.IN_PROGRESS)
        assertThat(fixture.store.records).allMatch { it.status=="RESUMED" }
    }

    private fun fixture(selectedEmployee:UUID):Fixture {
        val repository=MemoryRepository()
        val history=MemoryHistory()
        val store=MemoryInterruptionStore(repository)
        lateinit var coordinator:AutomaticFlashInterruptionService
        val assignment=TaskCreationAssignmentOrchestrator { _,at -> AutomaticAssignmentResult(TaskAssignment(TaskAssigneeType.USER,selectedEmployee.toString(),"Employee",at),"AUTO_ASSIGNED",selectedEmployee,1) }
        val handler=AutomaticFlashInterruptionHandler { task,employee,at -> coordinator.assigned(task,employee,at) }
        val observer=TaskCompletionObserver { task -> coordinator.completed(task) }
        val testClock=MutableTestClock(now.plusSeconds(2))
        val lifecycle=TaskLifecycleService(repository,history,completionPolicy=NoOpCompletionPolicy(),clock=Clock.fixed(now,ZoneOffset.UTC),completionObservers=listOf(observer),taskCreationAssignmentOrchestrator=assignment,automaticFlashInterruptionHandler=handler)
        val smart=SmartInterruptionService(store,repository,lifecycle,history,OperationalObservability.noop(),testClock)
        coordinator=AutomaticFlashInterruptionService(repository,smart)
        return Fixture(repository,store,lifecycle,testClock)
    }

    private fun command(intent:TaskIntentType,priority:TaskPriority,assignment:AssignmentCommand?)=CreateTaskCommand(hotelId,intent,TaskSource.PMS,intent.name,intent.name,"101",priority,now.plusSeconds(3600),assignment)
    private data class Fixture(val repository:MemoryRepository,val store:MemoryInterruptionStore,val lifecycle:TaskLifecycleService,val clock:MutableTestClock)
}

private class MutableTestClock(var current:Instant):Clock() {
    override fun getZone():ZoneId=ZoneOffset.UTC
    override fun withZone(zone:ZoneId):Clock=this
    override fun instant():Instant=current
}

private class MemoryRepository:TaskRepository {
    private val values=linkedMapOf<UUID,Task>()
    override fun save(task:Task)=task.also { values[it.id]=it }
    override fun findById(id:UUID)=values[id]
    override fun findAll()=values.values.toList()
    override fun findPage(request:TaskPageRequest)=TaskPage(findAll(),request.page,request.size,values.size.toLong())
}

private class MemoryHistory:TaskStateHistoryRepository {
    private val values=mutableListOf<TaskStateHistoryEntry>()
    override fun append(entry:TaskStateHistoryEntry){ values += entry }
    override fun findByTaskId(taskId:UUID)=values.filter { it.taskId==taskId }
    override fun findByTaskIds(taskIds:Collection<UUID>)=values.filter { it.taskId in taskIds }.groupBy(TaskStateHistoryEntry::taskId)
}

private class MemoryInterruptionStore(private val tasks:TaskRepository):TaskInterruptionStore {
    val records=mutableListOf<TaskInterruptionRecord>()
    override fun find(id:UUID,hotelId:UUID)=records.find { it.interruptionId==id && it.hotelId==hotelId }
    override fun findByIdempotencyKey(hotelId:UUID,key:String)=records.firstOrNull { it.hotelId==hotelId && "automatic-flash:${it.interruptingTaskId}"==key }
    override fun insert(record:TaskInterruptionRecord,idempotencyKey:String){ if(findByIdempotencyKey(record.hotelId,idempotencyKey)==null) records += record }
    override fun findActiveByInterruptingTaskId(hotelId:UUID,interruptingTaskId:UUID)=records.find { it.hotelId==hotelId && it.interruptingTaskId==interruptingTaskId && it.status=="ACTIVE" }
    override fun activeForEmployee(hotelId:UUID,employeeId:UUID)=records.filter { it.hotelId==hotelId && it.employeeId==employeeId && it.status=="ACTIVE" }.sortedByDescending(TaskInterruptionRecord::pausedAt).map { record ->
        val paused=tasks.findById(record.pausedTaskId);val interrupting=tasks.findById(record.interruptingTaskId)
        ActiveTaskInterruption(record,paused?.status,paused?.assignment?.assigneeType,paused?.assignment?.assigneeId,interrupting?.status)
    }
    override fun transition(id:UUID,hotelId:UUID,expectedStatus:String,status:String,resumedAt:Instant?):Boolean {
        val index=records.indexOfFirst { it.interruptionId==id && it.hotelId==hotelId && it.status==expectedStatus }
        if(index<0)return false
        records[index]=records[index].copy(status=status)
        return true
    }
}
