package com.hotelopai.task.application

import com.hotelopai.observability.OperationalObservability
import com.hotelopai.task.domain.Task
import com.hotelopai.task.domain.TaskAssigneeType
import com.hotelopai.task.domain.TaskAssignment
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import com.hotelopai.task.domain.TaskStatus
import com.hotelopai.task.domain.TaskTransition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class SmartInterruptionServiceTest {
    private val hotelId=UUID.randomUUID()
    private val employeeId=UUID.randomUUID()
    private val tasks=linkedMapOf<UUID,Task>()
    private val histories=mutableMapOf<UUID,List<TaskStateHistoryEntry>>()
    private val store=FakeTaskInterruptionStore(tasks)
    private val repository=FakeTaskRepository(tasks)
    private val lifecycle=mock(TaskLifecycleService::class.java)
    private val history=FakeTaskHistoryRepository(histories)
    private val clock=MutableClock(Instant.parse("2026-08-28T10:00:00Z"))
    private lateinit var service:SmartInterruptionService
    private lateinit var a:Task
    private lateinit var b:Task
    private lateinit var c:Task

    @BeforeEach fun setUp() {
        a=task("A",TaskPriority.MEDIUM,TaskStatus.STARTED)
        b=task("B",TaskPriority.URGENT,TaskStatus.ASSIGNED)
        c=task("C",TaskPriority.URGENT,TaskStatus.ASSIGNED)
        tasks.putAll(listOf(a,b,c).associateBy { it.id })
        wireLifecycle()
        service=SmartInterruptionService(store,repository,lifecycle,history,OperationalObservability.noop(),clock)
    }

    @Test fun `flash interruption explicitly persists typed source and separate reason`() {
        val result=interrupt(a,b,"Guest escalation")
        val persisted=store.records.single()
        assertThat(result.source).isEqualTo(InterruptionSource.FLASH_INTERRUPTION)
        assertThat(persisted.source).isEqualTo(InterruptionSource.FLASH_INTERRUPTION)
        assertThat(persisted.reason).isEqualTo("Guest escalation")
    }

    @Test fun `JDBC insert explicitly writes FLASH source column`() {
        val jdbc=mock(NamedParameterJdbcTemplate::class.java)
        val record=record(a,b,InterruptionSource.FLASH_INTERRUPTION)
        JdbcTaskInterruptionStore(jdbc).insert(record,"flash-key")
        @Suppress("UNCHECKED_CAST")
        val parameters=ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String,*>>
        val sql=ArgumentCaptor.forClass(String::class.java)
        verify(jdbc).update(sql.capture(),parameters.capture())
        assertThat(sql.value).contains("reason,source,status")
        assertThat(sql.value).contains(":key,:pausedAt,:pausedAt")
        assertThat(parameters.value["source"]).isEqualTo("FLASH_INTERRUPTION")
        assertThat(parameters.value["pausedAt"]).isEqualTo(Timestamp.from(record.pausedAt))
        assertThat(parameters.value["pausedAt"]).isInstanceOf(Timestamp::class.java)
    }

    @Test fun `JDBC resume transition binds resumedAt as Timestamp`() {
        val jdbc=mock(NamedParameterJdbcTemplate::class.java)
        val resumedAt=clock.instant().plusSeconds(60)

        JdbcTaskInterruptionStore(jdbc).transition(UUID.randomUUID(),hotelId,"RESUMING","RESUMED",resumedAt)

        @Suppress("UNCHECKED_CAST")
        val parameters=ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String,*>>
        verify(jdbc).update(org.mockito.ArgumentMatchers.anyString(),parameters.capture())
        assertThat(parameters.value["resumedAt"]).isEqualTo(Timestamp.from(resumedAt))
        assertThat(parameters.value["resumedAt"]).isInstanceOf(Timestamp::class.java)
    }

    @Test fun `source column reads back as typed enum`() {
        val rs=mock(ResultSet::class.java)
        val record=record(a,b,InterruptionSource.FLASH_INTERRUPTION)
        `when`(rs.getObject("id",UUID::class.java)).thenReturn(record.interruptionId)
        `when`(rs.getObject("hotel_id",UUID::class.java)).thenReturn(hotelId)
        `when`(rs.getObject("employee_id",UUID::class.java)).thenReturn(employeeId)
        `when`(rs.getObject("paused_task_id",UUID::class.java)).thenReturn(a.id)
        `when`(rs.getObject("interrupting_task_id",UUID::class.java)).thenReturn(b.id)
        `when`(rs.getString("reason")).thenReturn("Readable reason")
        `when`(rs.getString("status")).thenReturn("ACTIVE")
        `when`(rs.getString("source")).thenReturn("FLASH_INTERRUPTION")
        `when`(rs.getTimestamp("paused_at")).thenReturn(Timestamp.from(clock.instant()))
        assertThat(taskInterruptionRecord(rs).source).isEqualTo(InterruptionSource.FLASH_INTERRUPTION)
        `when`(rs.getString("source")).thenReturn("MANUAL")
        assertThat(taskInterruptionRecord(rs).source).isEqualTo(InterruptionSource.MANUAL)
    }

    @Test fun `A active then B flash auto pauses A`() {
        interrupt(a,b)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.WAITING)
        verify(lifecycle).pauseTask(a.id.toString(),hotelId,clock.instant())
    }

    @Test fun `completing B resumes A when persisted state is safe`() {
        val interruption=interrupt(a,b)
        clock.advanceSeconds(60)
        val result=service.completeAndResume(interruption.interruptionId,hotelId)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.IN_PROGRESS)
        assertThat(result.status).isEqualTo("RESUMED")
        verify(lifecycle,times(1)).resumeTask(a.id.toString(),hotelId,clock.instant())
    }

    @Test fun `manual lifecycle action after automatic pause prevents resume`() {
        val interruption=interrupt(a,b)
        histories[a.id]=listOf(history(a.id,TaskTransition.RESUME,clock.instant().plusSeconds(10)))
        clock.advanceSeconds(60)
        service.completeAndResume(interruption.interruptionId,hotelId)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.WAITING)
        assertThat(store.records.single().status).isEqualTo("CLOSED")
    }

    @Test fun `manual interruption source never auto resumes`() {
        tasks[a.id]=a.wait(clock.instant())
        tasks[b.id]=b.start(clock.instant())
        val manual=record(tasks.getValue(a.id),tasks.getValue(b.id),InterruptionSource.MANUAL)
        store.records+=manual
        clock.advanceSeconds(60)
        service.completeAndResume(manual.interruptionId,hotelId)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.WAITING)
        assertThat(store.records.single().status).isEqualTo("CLOSED")
    }

    @Test fun `reassigned A does not resume`() {
        val interruption=interrupt(a,b)
        tasks[a.id]=tasks.getValue(a.id).copy(assignment=assignment(UUID.randomUUID()),updatedAt=clock.instant().plusSeconds(10))
        histories[a.id]=listOf(history(a.id,TaskTransition.ASSIGN,clock.instant().plusSeconds(10)))
        clock.advanceSeconds(60)
        service.completeAndResume(interruption.interruptionId,hotelId)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.WAITING)
    }

    @Test fun `completed or cancelled A does not resume`() {
        listOf(TaskStatus.COMPLETED,TaskStatus.CANCELLED).forEach { terminal ->
            resetScenario()
            val interruption=interrupt(a,b)
            tasks[a.id]=tasks.getValue(a.id).copy(status=terminal)
            clock.advanceSeconds(60)
            service.completeAndResume(interruption.interruptionId,hotelId)
            assertThat(tasks.getValue(a.id).status).isEqualTo(terminal)
        }
    }

    @Test fun `A outside expected waiting state does not resume`() {
        val interruption=interrupt(a,b)
        tasks[a.id]=tasks.getValue(a.id).copy(status=TaskStatus.ASSIGNED)
        clock.advanceSeconds(60)
        service.completeAndResume(interruption.interruptionId,hotelId)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.ASSIGNED)
        assertThat(store.records.single().status).isEqualTo("CLOSED")
    }

    @Test fun `B completion leaves A paused while newer C is active`() {
        val first=interrupt(a,b)
        clock.advanceSeconds(60)
        interrupt(tasks.getValue(b.id),c)
        clock.advanceSeconds(60)
        service.completeAndResume(first.interruptionId,hotelId)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.WAITING)
        assertThat(store.records.first { it.interruptionId==first.interruptionId }.status).isEqualTo("ACTIVE")
    }

    @Test fun `completing final C resumes A exactly once and repeated completion is safe`() {
        val first=interrupt(a,b)
        clock.advanceSeconds(60)
        val second=interrupt(tasks.getValue(b.id),c)
        clock.advanceSeconds(60)
        service.completeAndResume(first.interruptionId,hotelId)
        clock.advanceSeconds(60)
        service.completeAndResume(second.interruptionId,hotelId)
        service.completeAndResume(second.interruptionId,hotelId)
        service.completeAndResume(first.interruptionId,hotelId)
        assertThat(tasks.getValue(a.id).status).isEqualTo(TaskStatus.IN_PROGRESS)
        verify(lifecycle,times(1)).resumeTask(a.id.toString(),hotelId,clock.instant())
    }

    private fun interrupt(active:Task,urgent:Task,reason:String="Flash") = service.interrupt(
        InterruptTaskCommand(hotelId,employeeId,"Employee",active.id,urgent.id,reason,"${active.id}:${urgent.id}")
    )

    private fun wireLifecycle() {
        doAnswer { invocation -> update(invocation.getArgument<String>(0)) { it.wait(invocation.getArgument(2)) } }
            .`when`(lifecycle).pauseTask(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(hotelId) ?: hotelId,org.mockito.ArgumentMatchers.any(Instant::class.java) ?: Instant.EPOCH)
        doAnswer { invocation -> update(invocation.getArgument<String>(0)) { it.start(invocation.getArgument(2)) } }
            .`when`(lifecycle).startTask(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(hotelId) ?: hotelId,org.mockito.ArgumentMatchers.any(Instant::class.java) ?: Instant.EPOCH)
        doAnswer { invocation -> update(invocation.getArgument<String>(0)) { it.complete(invocation.getArgument(2)) } }
            .`when`(lifecycle).completeTask(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(hotelId) ?: hotelId,org.mockito.ArgumentMatchers.any(Instant::class.java) ?: Instant.EPOCH)
        doAnswer { invocation -> update(invocation.getArgument<String>(0)) { it.progress(invocation.getArgument(2)) } }
            .`when`(lifecycle).resumeTask(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(hotelId) ?: hotelId,org.mockito.ArgumentMatchers.any(Instant::class.java) ?: Instant.EPOCH)
    }

    private fun update(id:String,change:(Task)->Task):Task=change(tasks.getValue(UUID.fromString(id))).also { tasks[it.id]=it }
    private fun task(title:String,priority:TaskPriority,status:TaskStatus):Task {
        val created=Instant.parse("2026-08-28T08:00:00Z")
        return Task.create(hotelId,TaskIntentType.FLASH_TASK,TaskSource.MANUAL,title,"Interruption test",priority=priority,slaDeadline=created.plusSeconds(86400),createdAt=created)
            .copy(status=status,assignment=assignment(employeeId),startedAt=created.plusSeconds(3600))
    }
    private fun assignment(id:UUID)=TaskAssignment(TaskAssigneeType.USER,id.toString(),"Employee",Instant.parse("2026-08-28T08:30:00Z"))
    private fun history(taskId:UUID,transition:TaskTransition,time:Instant)=TaskStateHistoryEntry(taskId,hotelId,TaskStatus.WAITING,TaskStatus.IN_PROGRESS,transition,occurredAt=time)
    private fun record(paused:Task,interrupting:Task,source:InterruptionSource)=TaskInterruptionRecord(UUID.randomUUID(),hotelId,employeeId,paused.id,interrupting.id,"Reason","ACTIVE",source,clock.instant())
    private fun resetScenario() { tasks.clear();store.records.clear();histories.clear();a=task("A",TaskPriority.MEDIUM,TaskStatus.STARTED);b=task("B",TaskPriority.URGENT,TaskStatus.ASSIGNED);c=task("C",TaskPriority.URGENT,TaskStatus.ASSIGNED);tasks.putAll(listOf(a,b,c).associateBy { it.id }) }
}

private class FakeTaskInterruptionStore(private val tasks:Map<UUID,Task>):TaskInterruptionStore {
    val records=mutableListOf<TaskInterruptionRecord>()
    override fun find(id:UUID,hotelId:UUID)=records.firstOrNull { it.interruptionId==id && it.hotelId==hotelId }
    override fun findByIdempotencyKey(hotelId:UUID,key:String)=records.firstOrNull { it.hotelId==hotelId && it.reason=="key:$key" }
    override fun insert(record:TaskInterruptionRecord,idempotencyKey:String) { records+=record }
    override fun activeForEmployee(hotelId:UUID,employeeId:UUID)=records.filter { it.hotelId==hotelId && it.employeeId==employeeId && it.status=="ACTIVE" }.sortedByDescending { it.pausedAt }.map { record ->
        val paused=tasks[record.pausedTaskId];val interrupting=tasks[record.interruptingTaskId]
        ActiveTaskInterruption(record,paused?.status,paused?.assignment?.assigneeType,paused?.assignment?.assigneeId,interrupting?.status)
    }
    override fun transition(id:UUID,hotelId:UUID,expectedStatus:String,status:String,resumedAt:Instant?):Boolean {
        val index=records.indexOfFirst { it.interruptionId==id && it.hotelId==hotelId && it.status==expectedStatus }
        if(index<0)return false
        records[index]=records[index].copy(status=status)
        return true
    }
}

private class FakeTaskRepository(private val tasks:MutableMap<UUID,Task>):TaskRepository {
    override fun save(task:Task)=task.also { tasks[it.id]=it }
    override fun findById(id:UUID)=tasks[id]
    override fun findByIdAndHotelId(id:UUID,hotelId:UUID)=tasks[id]?.takeIf { it.hotelId==hotelId }
    override fun findAll()=tasks.values.toList()
    override fun findPage(request:TaskPageRequest)=TaskPage(tasks.values.toList(),request.page,request.size,tasks.size.toLong())
}

private class FakeTaskHistoryRepository(private val histories:Map<UUID,List<TaskStateHistoryEntry>>):TaskStateHistoryRepository {
    override fun append(entry:TaskStateHistoryEntry)=Unit
    override fun findByTaskId(taskId:UUID)=histories[taskId].orEmpty()
    override fun findByTaskIds(taskIds:Collection<UUID>)=taskIds.associateWith { histories[it].orEmpty() }
}

private class MutableClock(private var current:Instant):Clock() {
    override fun getZone():ZoneId=ZoneOffset.UTC
    override fun withZone(zone:ZoneId):Clock=this
    override fun instant():Instant=current
    fun advanceSeconds(seconds:Long) { current=current.plusSeconds(seconds) }
}
