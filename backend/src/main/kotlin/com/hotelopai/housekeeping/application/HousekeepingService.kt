package com.hotelopai.housekeeping.application

import com.hotelopai.housekeeping.domain.*
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import com.hotelopai.pms.application.RoomReadyService
import org.springframework.beans.factory.annotation.Value

data class CreateHousekeepingCommand(val hotelId: UUID, val roomNumber: String, val type: HousekeepingWorkflowType, val inspectionRequired: Boolean, val idempotencyKey: String)
data class InspectHousekeepingCommand(val result: InspectionResult, val rejectionReason: String?, val qualityScore: Int?, val answers: List<InspectionAnswer>)

@Service
@Transactional
class HousekeepingService(
    private val repository: HousekeepingRepository,
    private val tasks: TaskLifecycleService,
    private val observability: OperationalObservability,
    private val roomReadyService: RoomReadyService,
    private val roomStates: RoomOperationalStateService? = null,
    private val minibarReadiness: MinibarReadinessService? = null,
    private val templates: InspectionTemplateService? = null,
    @Value("\${ops.ai.housekeeping.room-ready.enabled:false}") private val roomReadyEnabled:Boolean,
    private val clock: Clock = Clock.systemUTC()
) {
    fun create(command: CreateHousekeepingCommand): HousekeepingWorkflow {
        require(command.roomNumber.isNotBlank()) { "roomNumber is required" }
        require(command.idempotencyKey.isNotBlank()) { "idempotencyKey is required" }
        repository.findByIdempotencyKey(command.hotelId, command.idempotencyKey)?.let { return it }
        val now = clock.instant()
        val task = tasks.createTask(CreateTaskCommand(
            hotelId=command.hotelId,intentType=TaskIntentType.HOUSEKEEPING,source=TaskSource.IMPORT,
            title=command.type.name.replace('_',' ').lowercase().replaceFirstChar(Char::uppercase),
            description="Housekeeping workflow for room ${command.roomNumber}",roomNumber=command.roomNumber,
            priority=if(command.type==HousekeepingWorkflowType.VIP_PREPARATION) TaskPriority.HIGH else TaskPriority.MEDIUM,
            slaDeadline=now.plus(Duration.ofHours(4))))
        val template=if(command.inspectionRequired) templates?.applicable(command.hotelId,command.type) else null
        return repository.insert(HousekeepingWorkflow(UuidV7Generator.generate(now),command.hotelId,task.id,command.type,command.roomNumber,
            HousekeepingStatus.CREATED,command.inspectionRequired,idempotencyKey=command.idempotencyKey,createdAt=now,updatedAt=now,templateId=template?.id,templateVersion=template?.version)).also {
            roomStates?.set(command.hotelId, command.roomNumber, if(command.type==HousekeepingWorkflowType.DEPARTURE_CLEANING) RoomOperationalStatus.DIRTY else RoomOperationalStatus.CLEANING, "housekeeping:${it.id}")
            metric("create","success")
        }
    }

    fun list(hotelId: UUID)=repository.list(hotelId)
    fun get(id: UUID, hotelId: UUID)=repository.findByIdAndHotelId(id,hotelId) ?: throw HousekeepingNotFoundException(id)
    fun assign(id:UUID,hotelId:UUID)=mutate(id,hotelId,"assign") { it.assign(clock.instant()) }
    fun accept(id:UUID,hotelId:UUID)=mutate(id,hotelId,"accept") { it.accept(clock.instant()) }
    fun start(id:UUID,hotelId:UUID)=mutate(id,hotelId,"start") { workflow -> tasks.startTask(workflow.taskId.toString(),hotelId,clock.instant()); roomStates?.set(hotelId,workflow.roomNumber,RoomOperationalStatus.CLEANING,"housekeeping:${workflow.id}"); workflow.start(clock.instant()) }
    fun pause(id:UUID,hotelId:UUID,waiting:Boolean=false)=mutate(id,hotelId,"pause") { workflow -> tasks.pauseTask(workflow.taskId.toString(),hotelId,clock.instant()); workflow.pause(clock.instant(),waiting) }
    fun resume(id:UUID,hotelId:UUID)=mutate(id,hotelId,"resume") { workflow -> tasks.resumeTask(workflow.taskId.toString(),hotelId,clock.instant()); workflow.resume(clock.instant()) }
    fun finishCleaning(id:UUID,hotelId:UUID)=mutate(id,hotelId,"finish_cleaning") { workflow ->
        val updated=workflow.finishCleaning(clock.instant()); if(!updated.inspectionRequired) {
            tasks.completeTask(workflow.taskId.toString(),hotelId,clock.instant())
            val ready=minibarReadiness?.reevaluateRoomReadiness(hotelId,workflow.roomNumber) ?: run { roomStates?.set(hotelId,workflow.roomNumber,RoomOperationalStatus.READY,"housekeeping:${workflow.id}");true }
            if(roomReadyEnabled && ready) roomReadyService.markReady(hotelId,workflow.roomNumber,"housekeeping-room-ready:${workflow.id}:no-inspection")
        } else roomStates?.set(hotelId,workflow.roomNumber,RoomOperationalStatus.INSPECTION_REQUIRED,"housekeeping:${workflow.id}"); updated }

    fun inspect(id:UUID,hotelId:UUID,inspectorUserId:UUID,command:InspectHousekeepingCommand):HousekeepingWorkflow {
        val current=get(id,hotelId); templates?.validate(current.templateId?.let{templates.get(hotelId,it)},command.answers); val now=clock.instant(); val updated=current.inspect(command.result,now)
        val history=repository.inspections(id,hotelId)
        repository.appendInspection(hotelId,HousekeepingInspection(UuidV7Generator.generate(now),id,inspectorUserId,history.size+1,command.result,command.rejectionReason,command.qualityScore,command.answers,current.inspectionStartedAt?:now,now))
        if(command.result==InspectionResult.PASS) {
            tasks.completeTask(current.taskId.toString(),hotelId,now)
            val ready=minibarReadiness?.reevaluateRoomReadiness(hotelId,current.roomNumber) ?: run { roomStates?.set(hotelId,current.roomNumber,RoomOperationalStatus.READY,"housekeeping:${current.id}");true }
            if(roomReadyEnabled && ready) roomReadyService.markReady(hotelId,current.roomNumber,"housekeeping-room-ready:${current.id}:${history.size+1}")
        } else roomStates?.set(hotelId,current.roomNumber,RoomOperationalStatus.REWORK,"housekeeping:${current.id}")
        return repository.save(updated).also { metric("inspect",command.result.name.lowercase()) }
    }

    fun close(id:UUID,hotelId:UUID)=mutate(id,hotelId,"close") { it.close(clock.instant()) }
    fun inspectionHistory(id:UUID,hotelId:UUID):List<HousekeepingInspection> { get(id,hotelId); return repository.inspections(id,hotelId) }

    private fun mutate(id:UUID,hotelId:UUID,operation:String,action:(HousekeepingWorkflow)->HousekeepingWorkflow):HousekeepingWorkflow =
        repository.save(action(get(id,hotelId))).also { metric(operation,"success") }
    private fun metric(operation:String,outcome:String)=observability.incrementCounter("hotelopai.housekeeping.lifecycle.total","operation" to operation,"outcome" to outcome)
}
