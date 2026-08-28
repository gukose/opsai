package com.hotelopai.integration.unimock.demo

import com.hotelopai.housekeeping.application.CreateHousekeepingCommand
import com.hotelopai.housekeeping.application.HousekeepingService
import com.hotelopai.housekeeping.application.RoomOperationalStateService
import com.hotelopai.housekeeping.application.RoomOperationalStatus
import com.hotelopai.housekeeping.domain.HousekeepingWorkflowType
import com.hotelopai.shared.kernel.UuidV7Generator
import com.hotelopai.task.application.CreateTaskCommand
import com.hotelopai.task.application.TaskLifecycleService
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class PmsDemoEventType { CHECK_IN,CHECK_OUT,ARRIVAL,DEPARTURE,DIRTY,CLEAN,OCCUPIED,VACANT,VIP,ROOM_MOVE,OOO,OOS }
data class PmsDemoEventRequest(val eventId:String,val hotelCode:String,val roomNumber:String,val eventType:PmsDemoEventType,val occurredAt:Instant,val toRoomNumber:String?=null,val guestName:String?=null,val vipCategory:String?=null,val reason:String?=null)
data class PmsDemoEventResponse(val eventId:String,val roomNumber:String,val eventType:PmsDemoEventType,val occurredAt:Instant,val status:String,val duplicate:Boolean,val resultType:String?=null,val resultId:UUID?=null)

@ConfigurationProperties("ops.ai.pms.demo-events")
data class PmsDemoEventProperties(val enabled:Boolean=false,val sharedKey:String="")

@RestController
@RequestMapping("/api/v1/integrations/pms/unimock/demo-events")
@EnableConfigurationProperties(PmsDemoEventProperties::class)
class PmsDemoEventController(private val service:PmsDemoEventIngestionService,private val properties:PmsDemoEventProperties) {
    @PostMapping
    fun ingest(@RequestHeader("X-Demo-Pms-Key",required=false) key:String?,@RequestBody request:PmsDemoEventRequest):PmsDemoEventResponse {
        if(!properties.enabled) throw ResponseStatusException(HttpStatus.NOT_FOUND,"PMS demo events are not enabled")
        if(properties.sharedKey.length<12||!MessageDigest.isEqual(properties.sharedKey.toByteArray(),key.orEmpty().toByteArray()))
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED,"PMS demo event authentication failed")
        return try { service.ingest(request) } catch(e:IllegalArgumentException) { throw ResponseStatusException(HttpStatus.BAD_REQUEST,e.message) }
    }
}

@Service
class PmsDemoEventIngestionService(
    private val jdbc:NamedParameterJdbcTemplate,
    private val housekeeping:HousekeepingService,
    private val roomStates:RoomOperationalStateService,
    private val tasks:TaskLifecycleService,
    private val clock:Clock
) {
    @Transactional
    fun ingest(request:PmsDemoEventRequest):PmsDemoEventResponse {
        require(request.eventId.matches(Regex("[A-Za-z0-9_-]{4,100}"))) { "Invalid event ID" }
        val hotelId=jdbc.query("select id from hotel where code=:code and status='ACTIVE'",mapOf("code" to request.hotelCode)){rs,_->rs.getObject(1,UUID::class.java)}.singleOrNull()
            ?: throw IllegalArgumentException("Hotel does not exist or is inactive")
        existing(hotelId,request.eventId)?.let { return it.copy(duplicate=true) }
        requireRoom(hotelId,request.roomNumber,"Room does not exist")
        if(request.eventType==PmsDemoEventType.ROOM_MOVE) requireRoom(hotelId,request.toRoomNumber.orEmpty(),"Destination room does not exist")
        val now=clock.instant()
        val inboxId=UuidV7Generator.generate(now)
        val inserted=jdbc.update("""insert into pms_demo_event_inbox(id,hotel_id,provider_event_id,event_type,room_number,destination_room_number,status,occurred_at,created_at)
            values(:id,:hotel,:event,:type,:room,:destination,'PROCESSING',:occurred,:now) on conflict(hotel_id,provider_event_id) do nothing""",
            mapOf("id" to inboxId,"hotel" to hotelId,"event" to request.eventId,"type" to request.eventType.name,"room" to request.roomNumber,"destination" to request.toRoomNumber,"occurred" to request.occurredAt,"now" to now))
        if(inserted==0) return existing(hotelId,request.eventId)!!.copy(duplicate=true)

        val result=applyRule(hotelId,request,now)
        jdbc.update("update pms_demo_event_inbox set status='PROCESSED',result_type=:resultType,result_id=:resultId,processed_at=:now where id=:id",
            mapOf("id" to inboxId,"resultType" to result?.first,"resultId" to result?.second,"now" to clock.instant()))
        return PmsDemoEventResponse(request.eventId,request.roomNumber,request.eventType,request.occurredAt,"PROCESSED",false,result?.first,result?.second)
    }

    private fun applyRule(hotelId:UUID,request:PmsDemoEventRequest,now:Instant):Pair<String,UUID>? = when(request.eventType) {
        PmsDemoEventType.CHECK_OUT,PmsDemoEventType.DEPARTURE -> housekeeping.create(CreateHousekeepingCommand(hotelId,request.roomNumber,HousekeepingWorkflowType.DEPARTURE_CLEANING,true,"pms:${request.eventId}")).let { "HOUSEKEEPING_WORKFLOW" to it.id }
        PmsDemoEventType.DIRTY -> housekeeping.create(CreateHousekeepingCommand(hotelId,request.roomNumber,HousekeepingWorkflowType.STAYOVER_CLEANING,false,"pms:${request.eventId}")).let { "HOUSEKEEPING_WORKFLOW" to it.id }
        PmsDemoEventType.VIP -> housekeeping.create(CreateHousekeepingCommand(hotelId,request.roomNumber,HousekeepingWorkflowType.VIP_PREPARATION,true,"pms:${request.eventId}")).let { "HOUSEKEEPING_WORKFLOW" to it.id }
        PmsDemoEventType.CLEAN -> { roomStates.set(hotelId,request.roomNumber,RoomOperationalStatus.READY,"pms:${request.eventId}");null }
        PmsDemoEventType.OOO,PmsDemoEventType.OOS -> tasks.createTask(CreateTaskCommand(hotelId,TaskIntentType.MAINTENANCE,TaskSource.IMPORT,
            if(request.eventType==PmsDemoEventType.OOO) "Room out of order" else "Room out of service",
            request.reason?.takeIf(String::isNotBlank)?:"PMS reported ${request.eventType.name}",request.roomNumber,TaskPriority.HIGH,now.plus(Duration.ofHours(2)))).let { "TASK" to it.id }
        PmsDemoEventType.CHECK_IN,PmsDemoEventType.ARRIVAL,PmsDemoEventType.OCCUPIED,PmsDemoEventType.VACANT,PmsDemoEventType.ROOM_MOVE -> null
    }

    private fun requireRoom(hotelId:UUID,room:String,message:String) { require(jdbc.queryForObject("select count(*) from room_master where hotel_id=:hotel and room_number=:room and active=true",mapOf("hotel" to hotelId,"room" to room),Long::class.java)==1L){message} }
    private fun existing(hotelId:UUID,eventId:String):PmsDemoEventResponse?=jdbc.query("select * from pms_demo_event_inbox where hotel_id=:hotel and provider_event_id=:event",mapOf("hotel" to hotelId,"event" to eventId)){rs,_->PmsDemoEventResponse(rs.getString("provider_event_id"),rs.getString("room_number"),PmsDemoEventType.valueOf(rs.getString("event_type")),rs.getTimestamp("occurred_at").toInstant(),rs.getString("status"),false,rs.getString("result_type"),rs.getObject("result_id",UUID::class.java))}.singleOrNull()
}
