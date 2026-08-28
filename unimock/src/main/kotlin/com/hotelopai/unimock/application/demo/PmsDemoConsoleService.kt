package com.hotelopai.unimock.application.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.unimock.application.pms.EventPushRequest
import com.hotelopai.unimock.application.pms.PmsReadService
import com.hotelopai.unimock.application.pms.PmsUpdateService
import com.hotelopai.unimock.config.UniMockProperties
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class DemoEventType { CHECK_IN,CHECK_OUT,ARRIVAL,DEPARTURE,DIRTY,CLEAN,OCCUPIED,VACANT,VIP,ROOM_MOVE,OOO,OOS }
data class DemoEventRequest(val eventId:String?=null,val roomNumber:String,val eventType:DemoEventType,val toRoomNumber:String?=null,val guestName:String?=null,val vipCategory:String?=null,val reason:String?=null)
data class DemoEventResult(val eventId:String,val roomNumber:String,val eventType:DemoEventType,val occurredAt:Instant,val status:String,val duplicate:Boolean=false,val message:String?=null)

class DemoEventDeliveryException(message:String):RuntimeException(message)

interface DemoEventDeliveryPort { fun deliver(payload:Map<String,Any?>):DemoEventResult }

@Service
class HotelOpAiDemoEventClient(private val properties:UniMockProperties,private val objectMapper:ObjectMapper):DemoEventDeliveryPort {
    override fun deliver(payload:Map<String,Any?>):DemoEventResult {
        require(properties.demoConsole.sharedKey.length>=12) { "Demo console delivery is not configured" }
        return RestClient.create(properties.demoConsole.hotelOpaiBaseUrl).post()
            .uri("/api/v1/integrations/pms/unimock/demo-events")
            .header("X-Demo-Pms-Key",properties.demoConsole.sharedKey)
            .body(payload).retrieve()
            .onStatus(HttpStatusCode::isError) { _,response ->
                val node=runCatching { objectMapper.readTree(response.body) }.getOrNull()
                throw DemoEventDeliveryException(node?.path("detail")?.asText()?.takeIf(String::isNotBlank) ?: "Event could not be delivered")
            }.body(DemoEventResult::class.java) ?: throw DemoEventDeliveryException("Event could not be delivered")
    }
}

@Service
class PmsDemoConsoleService(
    private val reads:PmsReadService,
    private val updates:PmsUpdateService,
    private val properties:UniMockProperties,
    private val delivery:DemoEventDeliveryPort,
    private val clock:Clock
) {
    fun rooms()=reads.listRooms().sortedBy { it.roomNumber }

    fun send(request:DemoEventRequest):DemoEventResult {
        require(rooms().any { it.roomNumber==request.roomNumber }) { "Room does not exist" }
        if(request.eventType==DemoEventType.ROOM_MOVE) {
            require(!request.toRoomNumber.isNullOrBlank()) { "Destination room is required" }
            require(request.toRoomNumber!=request.roomNumber) { "Destination room must be different" }
            require(rooms().any { it.roomNumber==request.toRoomNumber }) { "Destination room does not exist" }
        }
        val eventId=request.eventId?.trim()?.takeIf(String::isNotEmpty) ?: "DEMO-${UUID.randomUUID()}"
        require(eventId.matches(Regex("[A-Za-z0-9_-]{4,100}"))) { "Event ID may contain only letters, numbers, dashes and underscores" }
        val occurredAt=clock.instant()
        val payload=mapOf("eventId" to eventId,"hotelCode" to properties.demoConsole.hotelCode,"roomNumber" to request.roomNumber,
            "eventType" to request.eventType.name,"occurredAt" to occurredAt.toString(),"toRoomNumber" to request.toRoomNumber,
            "guestName" to request.guestName,"vipCategory" to request.vipCategory,"reason" to request.reason)
        return try {
            val body=delivery.deliver(payload)
            record(request,eventId,occurredAt,body.status,null)
            body
        } catch(e:Exception) {
            val message=when(e) { is IllegalArgumentException,is DemoEventDeliveryException -> e.message ?: "Invalid event data"; else -> "Event could not be delivered" }
            record(request,eventId,occurredAt,"FAILED",message)
            throw DemoEventDeliveryException(message)
        }
    }

    private fun record(request:DemoEventRequest,eventId:String,occurredAt:Instant,status:String,message:String?) {
        updates.pushEvent(EventPushRequest(eventId,request.eventType.name,occurredAt.toString(),request.roomNumber,request.toRoomNumber,status,message),"demo-console")
    }
}
