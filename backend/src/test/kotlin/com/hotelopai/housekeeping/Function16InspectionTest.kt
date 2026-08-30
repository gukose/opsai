package com.hotelopai.housekeeping

import com.hotelopai.housekeeping.application.*
import com.hotelopai.housekeeping.domain.*
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.RoomReadyService
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.*
import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeStatus
import com.hotelopai.employee.application.EmployeeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class Function16InspectionTest {
    private val hotel=UUID.randomUUID(); private val taskId=UUID.randomUUID(); private val workflowId=UUID.randomUUID(); private val supervisor=UUID.randomUUID(); private val item1=UUID.randomUUID(); private val item2=UUID.randomUUID(); private val now=Instant.parse("2026-08-28T10:00:00Z")

    @Test fun `cleaning complete enters inspection and task waiting`() {
        val workflow=workflow(HousekeepingStatus.STARTED); val repo=mock(HousekeepingRepository::class.java); doReturn(workflow).`when`(repo).findByIdAndHotelId(workflowId,hotel)
        val tasks=mock(TaskLifecycleService::class.java); val service=service(repo,tasks)
        service.finishCleaning(workflowId,hotel)
        verify(tasks).pauseTask(taskId.toString(),hotel,now)
        verify(repo).save(org.mockito.ArgumentMatchers.any(HousekeepingWorkflow::class.java) ?: workflow(HousekeepingStatus.INSPECTION))
    }

    @Test fun `approval calculates score persists history completes task and reevaluates readiness`() {
        val workflow=workflow(HousekeepingStatus.INSPECTION).copy(templateId=UUID.randomUUID()); val repo=mock(HousekeepingRepository::class.java); doReturn(workflow).`when`(repo).findForUpdate(workflowId,hotel); doReturn(emptyList<HousekeepingInspection>()).`when`(repo).inspections(workflowId,hotel)
        val templates=templateService(workflow.templateId!!); val tasks=mock(TaskLifecycleService::class.java); val readiness=mock(MinibarReadinessService::class.java); doReturn(false).`when`(readiness).reevaluateRoomReadiness(hotel,"205"); val service=service(repo,tasks,readiness,templates)
        service.inspect(workflowId,hotel,supervisor,InspectHousekeepingCommand(InspectionResult.PASS,null,0,listOf(InspectionAnswer(item1,true),InspectionAnswer(item2,true))))
        verify(tasks).completeTask(taskId.toString(),hotel,now)
        verify(repo).appendInspection(eq(hotel) ?: hotel,org.mockito.ArgumentMatchers.any(HousekeepingInspection::class.java) ?: HousekeepingInspection(UUID.randomUUID(),workflowId,supervisor,1,InspectionResult.PASS,null,100,emptyList(),now,now))
        verify(readiness).reevaluateRoomReadiness(hotel,"205")
    }

    @Test fun `rejection persists score and returns same task to correction`() {
        val workflow=workflow(HousekeepingStatus.INSPECTION).copy(templateId=UUID.randomUUID()); val repo=mock(HousekeepingRepository::class.java); doReturn(workflow).`when`(repo).findForUpdate(workflowId,hotel); doReturn(emptyList<HousekeepingInspection>()).`when`(repo).inspections(workflowId,hotel)
        val tasks=mock(TaskLifecycleService::class.java); val service=service(repo,tasks,templates=templateService(workflow.templateId!!))
        service.inspect(workflowId,hotel,supervisor,InspectHousekeepingCommand(InspectionResult.REJECT,"Bathroom failed",null,listOf(InspectionAnswer(item1,true),InspectionAnswer(item2,false))))
        verify(tasks).resumeTask(taskId.toString(),hotel,now)
        verify(repo).save(org.mockito.ArgumentMatchers.any(HousekeepingWorkflow::class.java) ?: workflow(HousekeepingStatus.REWORK))
        verify(repo).appendInspection(eq(hotel) ?: hotel,org.mockito.ArgumentMatchers.any(HousekeepingInspection::class.java) ?: HousekeepingInspection(UUID.randomUUID(),workflowId,supervisor,1,InspectionResult.REJECT,"Bathroom failed",50,emptyList(),now,now))
    }

    @Test fun `approval does not require checklist answers for MVP`() {
        val workflow=workflow(HousekeepingStatus.INSPECTION).copy(templateId=UUID.randomUUID()); val repo=mock(HousekeepingRepository::class.java); doReturn(workflow).`when`(repo).findForUpdate(workflowId,hotel); doReturn(emptyList<HousekeepingInspection>()).`when`(repo).inspections(workflowId,hotel)
        val service=service(repo,mock(TaskLifecycleService::class.java),templates=templateService(workflow.templateId!!))
        service.inspect(workflowId,hotel,supervisor,InspectHousekeepingCommand(InspectionResult.PASS,null,null,emptyList()))
        verify(repo).appendInspection(eq(hotel) ?: hotel, org.mockito.ArgumentMatchers.any(HousekeepingInspection::class.java) ?: HousekeepingInspection(UUID.randomUUID(),workflowId,supervisor,1,InspectionResult.PASS,null,100,emptyList(),now,now))
    }

    @Test fun `assigned housekeeper cannot approve own inspection even with permission`() {
        val workflow=workflow(HousekeepingStatus.INSPECTION).copy(templateId=UUID.randomUUID()); val repo=mock(HousekeepingRepository::class.java); doReturn(workflow).`when`(repo).findForUpdate(workflowId,hotel); doReturn(emptyList<HousekeepingInspection>()).`when`(repo).inspections(workflowId,hotel)
        val tasks=mock(TaskLifecycleService::class.java); val employeeRepo=mock(EmployeeRepository::class.java); val employee=employee(supervisor); val task=Task.create(hotel,TaskIntentType.HOUSEKEEPING,TaskSource.IMPORT,"Cleaning","Cleaning",roomNumber="205",priority=TaskPriority.MEDIUM,slaDeadline=now.plusSeconds(3600),createdAt=now,id=taskId).assign(TaskAssignment(TaskAssigneeType.USER,supervisor.toString(),"Housekeeper",now),now)
        doReturn(task).`when`(tasks).getTaskForHotel(taskId.toString(),hotel); doReturn(employee).`when`(employeeRepo).findById(supervisor); doReturn(employee).`when`(employeeRepo).findByUserId(supervisor)
        val service=service(repo,tasks,templates=templateService(workflow.templateId!!),employees=employeeRepo)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) { service.inspect(workflowId,hotel,supervisor,InspectHousekeepingCommand(InspectionResult.REJECT,"not clean",null,emptyList()),actingEmployeeId=supervisor) }
    }

    private fun service(repo:HousekeepingRepository,tasks:TaskLifecycleService,readiness:MinibarReadinessService?=null,templates:InspectionTemplateService?=null,employees:EmployeeRepository?=null):HousekeepingService {
        doAnswer { it.arguments[0] }.`when`(repo).save(org.mockito.ArgumentMatchers.any(HousekeepingWorkflow::class.java) ?: workflow(HousekeepingStatus.CREATED))
        return HousekeepingService(repo,tasks,OperationalObservability.noop(),mock(RoomReadyService::class.java),roomStates=null,minibarReadiness=readiness,templates=templates,employees=employees,roomReadyEnabled=false,clock=Clock.fixed(now,ZoneOffset.UTC))
    }
    private fun workflow(status:HousekeepingStatus)=HousekeepingWorkflow(workflowId,hotel,taskId,HousekeepingWorkflowType.DEPARTURE_CLEANING,"205",status,true,idempotencyKey="test",createdAt=now,updatedAt=now)
    private fun templateService(id:UUID):InspectionTemplateService { val service=mock(InspectionTemplateService::class.java); doReturn(InspectionTemplate(id,HousekeepingWorkflowType.DEPARTURE_CLEANING,"Room",1,true,listOf(InspectionTemplateItem(item1,"BED","Bed",true,1),InspectionTemplateItem(item2,"BATH","Bath",true,2)))).`when`(service).get(hotel,id); return service }
    private fun employee(id:UUID)=Employee(id,hotel,employeeNumber="E",displayName="Housekeeper",status=EmployeeStatus.ACTIVE,createdAt=now)
}
