package com.hotelopai.housekeeping.api

import com.hotelopai.housekeeping.application.*
import com.hotelopai.housekeeping.domain.*
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateHousekeepingRequest(val roomNumber:String,val type:HousekeepingWorkflowType,val inspectionRequired:Boolean=true,val idempotencyKey:String)
data class InspectionAnswerRequest(val checklistItemId:UUID,val passed:Boolean,val note:String?=null)
data class InspectHousekeepingRequest(val result:InspectionResult,val rejectionReason:String?=null,val qualityScore:Int?=null,val answers:List<InspectionAnswerRequest> = emptyList())

@RestController
@RequestMapping("/api/v1/internal/housekeeping")
class InternalHousekeepingController(private val service:HousekeepingService,private val current:CurrentUserContextResolver) {
    @GetMapping @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS)
    fun list()=service.list(current.current().hotelId)
    @PostMapping @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS)
    fun create(@RequestBody r:CreateHousekeepingRequest)=service.create(CreateHousekeepingCommand(current.current().hotelId,r.roomNumber,r.type,r.inspectionRequired,r.idempotencyKey))
    @GetMapping("/{id}") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS)
    fun get(@PathVariable id:UUID)=service.get(id,current.current().hotelId)
    @PostMapping("/{id}/assign") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun assign(@PathVariable id:UUID)=service.assign(id,current.current().hotelId)
    @PostMapping("/{id}/accept") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun accept(@PathVariable id:UUID)=service.accept(id,current.current().hotelId)
    @PostMapping("/{id}/start") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun start(@PathVariable id:UUID)=service.start(id,current.current().hotelId)
    @PostMapping("/{id}/pause") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun pause(@PathVariable id:UUID,@RequestParam(defaultValue="false") waiting:Boolean)=service.pause(id,current.current().hotelId,waiting)
    @PostMapping("/{id}/resume") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun resume(@PathVariable id:UUID)=service.resume(id,current.current().hotelId)
    @PostMapping("/{id}/finish-cleaning") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun finish(@PathVariable id:UUID)=service.finishCleaning(id,current.current().hotelId)
    @PostMapping("/{id}/inspect") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_INSPECTION)
    fun inspect(@PathVariable id:UUID,@RequestBody r:InspectHousekeepingRequest)=service.inspect(id,current.current().hotelId,current.current().userId,InspectHousekeepingCommand(r.result,r.rejectionReason,r.qualityScore,r.answers.map { InspectionAnswer(it.checklistItemId,it.passed,it.note) }))
    @GetMapping("/{id}/inspections") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_INSPECTION) fun inspections(@PathVariable id:UUID)=service.inspectionHistory(id,current.current().hotelId)
    @PostMapping("/{id}/close") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_OPERATIONS) fun close(@PathVariable id:UUID)=service.close(id,current.current().hotelId)
}

@RestController @RequestMapping("/api/v1/internal/housekeeping/inspection-templates")
class InternalInspectionTemplateController(private val templates:InspectionTemplateService,private val current:CurrentUserContextResolver){
 @GetMapping @PreAuthorize(PermissionExpressions.HOUSEKEEPING_INSPECTION) fun list()=templates.list(current.current().hotelId)
 @PostMapping @PreAuthorize(PermissionExpressions.HOUSEKEEPING_INSPECTION) fun create(@RequestBody request:CreateInspectionTemplate)=templates.createVersion(current.current().hotelId,request)
 @DeleteMapping("/{id}") @PreAuthorize(PermissionExpressions.HOUSEKEEPING_INSPECTION) fun disable(@PathVariable id:UUID)=templates.disable(current.current().hotelId,id)
}
