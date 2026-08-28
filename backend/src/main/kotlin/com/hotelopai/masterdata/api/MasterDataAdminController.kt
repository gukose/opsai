package com.hotelopai.masterdata.api

import com.hotelopai.masterdata.application.*
import com.hotelopai.shared.error.ProblemDetailFactory
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionCodes
import com.hotelopai.shared.security.PermissionExpressions
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class HotelRequest(@field:NotBlank val code:String="",@field:NotBlank val name:String,val timezone:String="UTC",val address:String?=null,val active:Boolean=true)
data class NamedRequest(@field:NotBlank val code:String,@field:NotBlank val name:String,val description:String?=null,val active:Boolean=true)
data class BuildingRequest(@field:NotBlank val code:String,@field:NotBlank val name:String,val active:Boolean=true)
data class FloorRequest(val buildingId:UUID,val floorNumber:Int,val name:String?=null,val active:Boolean=true)
data class RoomRequest(val buildingId:UUID,val floorId:UUID,@field:NotBlank val roomNumber:String,val roomType:String?=null,val active:Boolean=true)
data class MembershipRequest(val userId:UUID,val departmentId:UUID?=null)
data class EmployeeUserRequest(val email:String,val displayName:String,val initialPassword:String,val departmentId:UUID?=null)
data class RoleAssignmentRequest(val roleId:UUID)
data class RolePermissionsRequest(val permissionIds:Set<UUID>)
data class SkillAssignmentRequest(val skillId:UUID,val skillLevel:String?=null)
data class MembershipUpdateRequest(val displayName:String?=null,val departmentId:UUID?=null,val active:Boolean=true)
data class MembershipRolesRequest(val roleIds:Set<UUID>)
data class MembershipSkillsRequest(val skills:Map<UUID,String?>)
data class ShiftRequest(@field:NotBlank val code:String,@field:NotBlank val name:String,val startTime:LocalTime,val endTime:LocalTime)
data class ShiftAssignmentRequest(val membershipId:UUID,val shiftId:UUID,val shiftDate:LocalDate)
data class HotelOnboardingRequest(@field:NotBlank val code:String,@field:NotBlank val name:String,val timezone:String="UTC",val address:String?=null,val departments:List<OnboardingDepartment> = emptyList(),val buildings:List<OnboardingBuilding> = emptyList(),val rooms:List<OnboardingRoom> = emptyList(),val skills:List<OnboardingSkill> = emptyList(),val shifts:List<OnboardingShift> = emptyList(),val administratorUserId:UUID,val administratorRoleId:UUID?=null)

@RestController
@RequestMapping("/api/v1/internal/admin")
class MasterDataAdminController(private val service:MasterDataAdminService,private val current:CurrentUserContextResolver){
 @GetMapping("/hotels") @PreAuthorize(PermissionExpressions.HOTEL_VIEW) fun hotels()=current.current().let{service.hotelsFor(it.userId,PermissionCodes.PLATFORM_HOTEL_MANAGE in it.permissions)}
 @PostMapping("/hotels") @PreAuthorize(PermissionExpressions.PLATFORM_HOTEL_MANAGE) fun createHotel(@Valid @RequestBody r:HotelRequest)=service.createHotel(r.code,r.name,r.timezone,r.address,current.current().userId)
 @PostMapping("/hotels/onboard") @PreAuthorize(PermissionExpressions.PLATFORM_HOTEL_MANAGE) fun onboard(@Valid @RequestBody r:HotelOnboardingRequest)=service.onboard(HotelOnboardingCommand(r.code,r.name,r.timezone,r.address,r.departments,r.buildings,r.rooms,r.skills,r.shifts,r.administratorUserId,r.administratorRoleId),current.current().userId)
 @GetMapping("/hotels/{hotelId}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'HOTEL_VIEW')") fun hotel(@PathVariable hotelId:UUID)=service.hotel(hotelId)
 @GetMapping("/hotels/{hotelId}/access") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'HOTEL_VIEW')") fun access(@PathVariable hotelId:UUID)=service.access(current.current().userId,hotelId)
 @PutMapping("/hotels/{hotelId}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'HOTEL_MANAGE')") fun updateHotel(@PathVariable hotelId:UUID,@Valid @RequestBody r:HotelRequest)=service.updateHotel(hotelId,r.name,r.timezone,r.address,r.active,current.current().userId)

 @GetMapping("/hotels/{hotelId}/buildings") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'BUILDING_VIEW')") fun buildings(@PathVariable hotelId:UUID)=service.buildings(hotelId)
 @PostMapping("/hotels/{hotelId}/buildings") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'BUILDING_MANAGE')") fun createBuilding(@PathVariable hotelId:UUID,@Valid @RequestBody r:BuildingRequest)=service.createBuilding(hotelId,r.code,r.name,current.current().userId)
 @PutMapping("/hotels/{hotelId}/buildings/{id}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'BUILDING_MANAGE')") fun updateBuilding(@PathVariable hotelId:UUID,@PathVariable id:UUID,@Valid @RequestBody r:BuildingRequest)=service.updateBuilding(hotelId,id,r.code,r.name,r.active,current.current().userId)

 @GetMapping("/hotels/{hotelId}/floors") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'FLOOR_VIEW')") fun floors(@PathVariable hotelId:UUID,@RequestParam(required=false) buildingId:UUID?)=service.floors(hotelId,buildingId)
 @PostMapping("/hotels/{hotelId}/floors") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'FLOOR_MANAGE')") fun createFloor(@PathVariable hotelId:UUID,@RequestBody r:FloorRequest)=service.createFloor(hotelId,r.buildingId,r.floorNumber,r.name,current.current().userId)
 @PutMapping("/hotels/{hotelId}/floors/{id}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'FLOOR_MANAGE')") fun updateFloor(@PathVariable hotelId:UUID,@PathVariable id:UUID,@RequestBody r:FloorRequest)=service.updateFloor(hotelId,id,r.buildingId,r.floorNumber,r.name,r.active,current.current().userId)

 @GetMapping("/hotels/{hotelId}/rooms") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROOM_VIEW')") fun rooms(@PathVariable hotelId:UUID,@RequestParam(required=false) q:String?,@RequestParam(required=false) buildingId:UUID?,@RequestParam(required=false) floorId:UUID?,@RequestParam(required=false) type:String?,@RequestParam(required=false) active:Boolean?,@RequestParam(defaultValue="0") page:Int,@RequestParam(defaultValue="25") size:Int)=service.rooms(hotelId,q,buildingId,floorId,type,active,page,size)
 @GetMapping("/hotels/{hotelId}/rooms/{id}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROOM_VIEW')") fun room(@PathVariable hotelId:UUID,@PathVariable id:UUID)=service.room(hotelId,id)
 @PostMapping("/hotels/{hotelId}/rooms") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROOM_CREATE')") fun createRoom(@PathVariable hotelId:UUID,@Valid @RequestBody r:RoomRequest)=service.createRoom(hotelId,r.buildingId,r.floorId,r.roomNumber,r.roomType,current.current().userId)
 @PutMapping("/hotels/{hotelId}/rooms/{id}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROOM_UPDATE')") fun updateRoom(@PathVariable hotelId:UUID,@PathVariable id:UUID,@Valid @RequestBody r:RoomRequest)=service.updateRoom(hotelId,id,r.buildingId,r.floorId,r.roomNumber,r.roomType,r.active,current.current().userId)
 @PostMapping("/hotels/{hotelId}/rooms/import") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROOM_CREATE')") fun importRooms(@PathVariable hotelId:UUID,@RequestBody csv:String)=service.importRooms(hotelId,csv,current.current().userId)

 @GetMapping("/hotels/{hotelId}/{kind:departments|skills|roles}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'HOTEL_VIEW')") fun named(@PathVariable hotelId:UUID,@PathVariable kind:String)=service.namedList(kind.dropLast(1),hotelId)
 @PostMapping("/hotels/{hotelId}/{kind:departments|skills|roles}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'HOTEL_MANAGE')") fun createNamed(@PathVariable hotelId:UUID,@PathVariable kind:String,@Valid @RequestBody r:NamedRequest)=service.createNamed(kind.dropLast(1),hotelId,r.code,r.name,r.description,current.current().userId)
 @PutMapping("/hotels/{hotelId}/{kind:departments|skills|roles}/{id}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'HOTEL_MANAGE')") fun updateNamed(@PathVariable hotelId:UUID,@PathVariable kind:String,@PathVariable id:UUID,@Valid @RequestBody r:NamedRequest)=service.updateNamedRecord(kind.dropLast(1),hotelId,id,r.code,r.name,r.description,r.active,current.current().userId)
 @GetMapping("/hotels/{hotelId}/roles/{roleId}/permissions") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROLE_VIEW')") fun rolePermissions(@PathVariable hotelId:UUID,@PathVariable roleId:UUID)=service.rolePermissions(hotelId,roleId)
 @PutMapping("/hotels/{hotelId}/roles/{roleId}/permissions") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROLE_MANAGE')") fun replaceRolePermissions(@PathVariable hotelId:UUID,@PathVariable roleId:UUID,@RequestBody r:RolePermissionsRequest)=service.replaceRolePermissions(hotelId,roleId,r.permissionIds)

 @GetMapping("/hotels/{hotelId}/memberships") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_VIEW')") fun memberships(@PathVariable hotelId:UUID)=service.memberships(hotelId)
 @GetMapping("/hotels/{hotelId}/employees") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_VIEW')") fun employees(@PathVariable hotelId:UUID)=service.memberships(hotelId)
 @PostMapping("/hotels/{hotelId}/memberships") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_ASSIGN')") fun addMembership(@PathVariable hotelId:UUID,@RequestBody r:MembershipRequest)=service.addMembership(hotelId,r.userId,r.departmentId,current.current().userId)
 @PostMapping("/hotels/{hotelId}/employees") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_CREATE')") fun addEmployee(@PathVariable hotelId:UUID,@RequestBody r:EmployeeUserRequest)=service.addOrCreateUser(hotelId,r.email,r.displayName,r.initialPassword,r.departmentId,current.current().userId)
 @GetMapping("/hotels/{hotelId}/memberships/{membershipId}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_VIEW')") fun membership(@PathVariable hotelId:UUID,@PathVariable membershipId:UUID)=service.membershipDetail(hotelId,membershipId)
 @PutMapping("/hotels/{hotelId}/memberships/{membershipId}") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_UPDATE')") fun updateMembership(@PathVariable hotelId:UUID,@PathVariable membershipId:UUID,@RequestBody r:MembershipUpdateRequest)=service.updateMembership(hotelId,membershipId,r.displayName,r.departmentId,r.active,current.current().userId)
 @PutMapping("/hotels/{hotelId}/memberships/{membershipId}/roles") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_ASSIGN')") fun replaceMembershipRoles(@PathVariable hotelId:UUID,@PathVariable membershipId:UUID,@RequestBody r:MembershipRolesRequest)=service.replaceMembershipRoles(hotelId,membershipId,r.roleIds,current.current().userId)
 @PutMapping("/hotels/{hotelId}/memberships/{membershipId}/skills") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'USER_ASSIGN')") fun replaceMembershipSkills(@PathVariable hotelId:UUID,@PathVariable membershipId:UUID,@RequestBody r:MembershipSkillsRequest)=service.replaceMembershipSkills(hotelId,membershipId,r.skills,current.current().userId)
 @PostMapping("/hotels/{hotelId}/memberships/{membershipId}/roles") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'ROLE_MANAGE')") fun assignRole(@PathVariable hotelId:UUID,@PathVariable membershipId:UUID,@RequestBody r:RoleAssignmentRequest)=service.assignRole(hotelId,membershipId,r.roleId,current.current().userId)
 @PostMapping("/hotels/{hotelId}/memberships/{membershipId}/skills") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'SKILL_MANAGE')") fun assignSkill(@PathVariable hotelId:UUID,@PathVariable membershipId:UUID,@RequestBody r:SkillAssignmentRequest)=service.assignSkill(hotelId,membershipId,r.skillId,r.skillLevel,current.current().userId)

 @GetMapping("/hotels/{hotelId}/shifts") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'SHIFT_VIEW')") fun shifts(@PathVariable hotelId:UUID)=service.shifts(hotelId)
 @PostMapping("/hotels/{hotelId}/shifts") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'SHIFT_MANAGE')") fun createShift(@PathVariable hotelId:UUID,@RequestBody r:ShiftRequest)=service.createShift(hotelId,r.code,r.name,r.startTime,r.endTime,current.current().userId)
 @GetMapping("/hotels/{hotelId}/shift-assignments") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'SHIFT_VIEW')") fun assignments(@PathVariable hotelId:UUID,@RequestParam(required=false) date:LocalDate?,@RequestParam(required=false) membershipId:UUID?,@RequestParam(required=false) shiftId:UUID?)=service.shiftAssignments(hotelId,date,membershipId,shiftId)
 @PostMapping("/hotels/{hotelId}/shift-assignments") @PreAuthorize("@permissionGuard.hasHotelPermission(#hotelId, 'SHIFT_MANAGE')") fun assignShift(@PathVariable hotelId:UUID,@RequestBody r:ShiftAssignmentRequest)=service.assignShift(hotelId,r.membershipId,r.shiftId,r.shiftDate,current.current().userId)
}

@RestControllerAdvice(assignableTypes=[MasterDataAdminController::class])
class MasterDataAdminExceptionHandler{
 @ExceptionHandler(MasterDataNotFound::class) @ResponseStatus(HttpStatus.NOT_FOUND) fun notFound(e:MasterDataNotFound):ProblemDetail=ProblemDetailFactory.create(HttpStatus.NOT_FOUND,"Not found",e.message?:"Resource not found")
 @ExceptionHandler(MasterDataConflict::class) @ResponseStatus(HttpStatus.CONFLICT) fun conflict(e:MasterDataConflict):ProblemDetail=ProblemDetailFactory.create(HttpStatus.CONFLICT,"Conflict",e.message?:"Conflicting master data")
 @ExceptionHandler(InactiveHotel::class) @ResponseStatus(HttpStatus.CONFLICT) fun inactive(e:InactiveHotel):ProblemDetail=ProblemDetailFactory.create(HttpStatus.CONFLICT,"Inactive hotel",e.message!!)
 @ExceptionHandler(IllegalArgumentException::class) @ResponseStatus(HttpStatus.BAD_REQUEST) fun bad(e:IllegalArgumentException):ProblemDetail=ProblemDetailFactory.create(HttpStatus.BAD_REQUEST,"Invalid request",e.message?:"Invalid request")
}
