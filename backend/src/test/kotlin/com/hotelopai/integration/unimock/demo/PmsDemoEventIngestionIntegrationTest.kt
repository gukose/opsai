package com.hotelopai.integration.unimock.demo

import com.hotelopai.support.PostgresIntegrationTestSupport
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class PmsDemoEventIngestionIntegrationTest:PostgresIntegrationTestSupport() {
    @Autowired lateinit var service:PmsDemoEventIngestionService
    @Autowired lateinit var jdbc:NamedParameterJdbcTemplate

    @Test fun `DIRTY traverses ingestion into one idempotent housekeeping workflow`() {
        val fixture=fixture("PMS_DIRTY")
        val event=request(fixture.code,"205",PmsDemoEventType.DIRTY,"DIRTY-1")
        val first=service.ingest(event)
        val duplicate=service.ingest(event)
        assertEquals("HOUSEKEEPING_WORKFLOW",first.resultType)
        assertTrue(duplicate.duplicate)
        assertEquals(first.resultId,duplicate.resultId)
        assertEquals(1,count("select count(*) from housekeeping_workflow where hotel_id=:hotel",fixture.hotel))
        assertEquals(1,count("select count(*) from task where hotel_id=:hotel",fixture.hotel))
    }

    @Test fun `CHECK_OUT creates one departure workflow one minibar task and pending readiness idempotently`() {
        val fixture=fixture("PMS_RULES")
        val event=request(fixture.code,"205",PmsDemoEventType.CHECK_OUT,"OUT-1")
        val first=service.ingest(event)
        val duplicate=service.ingest(event)
        assertEquals("HOUSEKEEPING_WORKFLOW",first.resultType)
        assertTrue(duplicate.duplicate)
        assertEquals(first.resultId,duplicate.resultId)
        assertEquals(1,count("select count(*) from housekeeping_workflow where hotel_id=:hotel",fixture.hotel))
        assertEquals(1,count("select count(*) from task where hotel_id=:hotel and intent_type='MINIBAR' and source='PMS'",fixture.hotel))
        assertEquals(1,count("select count(*) from room_minibar_readiness where hotel_id=:hotel and status='PENDING'",fixture.hotel))
        assertEquals(1,count("select count(*) from housekeeping_workflow where hotel_id=:hotel and task_id=(select id from task where hotel_id=:hotel and intent_type='HOUSEKEEPING' order by created_at desc limit 1) and inspection_required=true",fixture.hotel))
    }

    @Test fun `OOO continues to use existing task engine`() {
        val fixture=fixture("PMS_OOO")
        assertEquals("TASK",service.ingest(request(fixture.code,"310",PmsDemoEventType.OOO,"OOO-1",reason="Water leak")).resultType)
        assertEquals(1,count("select count(*) from task where hotel_id=:hotel",fixture.hotel))
    }

    @Test fun `ROOM_MOVE validates both rooms and creates no unrelated task`() {
        val fixture=fixture("PMS_MOVE")
        val result=service.ingest(request(fixture.code,"205",PmsDemoEventType.ROOM_MOVE,"MOVE-1",to="207"))
        assertNull(result.resultType)
        assertEquals(0,count("select count(*) from task where hotel_id=:hotel",fixture.hotel))
        assertThrows<IllegalArgumentException>{service.ingest(request(fixture.code,"205",PmsDemoEventType.ROOM_MOVE,"MOVE-2",to="999"))}
    }

    @Test fun `invalid source room is rejected without inbox residue`() {
        val fixture=fixture("PMS_INVALID")
        assertThrows<IllegalArgumentException>{service.ingest(request(fixture.code,"999",PmsDemoEventType.DIRTY,"BAD-1"))}
        assertEquals(0,count("select count(*) from pms_demo_event_inbox where hotel_id=:hotel",fixture.hotel))
    }

    private fun request(code:String,room:String,type:PmsDemoEventType,id:String,to:String?=null,reason:String?=null)=PmsDemoEventRequest(id,code,room,type,Instant.parse("2026-08-28T10:00:00Z"),toRoomNumber=to,reason=reason)
    private data class Fixture(val hotel:UUID,val code:String)
    private fun fixture(prefix:String):Fixture {
        val suffix=UUID.randomUUID().toString().take(8);val hotel=UUID.randomUUID();val building=UUID.randomUUID();val floor205=UUID.randomUUID();val floor310=UUID.randomUUID();val now=Instant.now();val code="${prefix}_$suffix"
        jdbc.update("insert into hotel(id,version,created_at,updated_at,code,name,status,timezone) values(:id,0,:now,:now,:code,'PMS Test','ACTIVE','UTC')",mapOf("id" to hotel,"now" to now,"code" to code))
        jdbc.update("insert into building(id,hotel_id,code,name,active,created_at,updated_at) values(:id,:hotel,'MAIN','Main',true,:now,:now)",mapOf("id" to building,"hotel" to hotel,"now" to now))
        jdbc.update("insert into hotel_floor(id,hotel_id,building_id,floor_number,active,created_at,updated_at) values(:id,:hotel,:building,2,true,:now,:now),(:id2,:hotel,:building,3,true,:now,:now)",mapOf("id" to floor205,"id2" to floor310,"hotel" to hotel,"building" to building,"now" to now))
        listOf("205" to floor205,"207" to floor205,"310" to floor310).forEach{(room,floor)->jdbc.update("insert into room_master(id,hotel_id,building_id,floor_id,room_number,active,created_at,updated_at) values(:id,:hotel,:building,:floor,:room,true,:now,:now)",mapOf("id" to UUID.randomUUID(),"hotel" to hotel,"building" to building,"floor" to floor,"room" to room,"now" to now))}
        return Fixture(hotel,code)
    }
    private fun count(sql:String,hotel:UUID)=jdbc.queryForObject(sql,mapOf("hotel" to hotel),Long::class.java)!!
}
