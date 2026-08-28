package com.hotelopai.masterdata

import com.hotelopai.masterdata.application.*
import com.hotelopai.support.PostgresIntegrationTestSupport
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class MasterDataAdminServiceIntegrationTest : PostgresIntegrationTestSupport() {
    @Autowired lateinit var service: MasterDataAdminService
    @Autowired lateinit var jdbc: NamedParameterJdbcTemplate

    @Test
    fun `hotel hierarchy is isolated unique and inactive-safe`() {
        val actor = existingUser()
        val a = service.createHotel("MH_A", "Hotel A", "Europe/Berlin", null, actor)
        val b = service.createHotel("MH_B", "Hotel B", "Europe/Berlin", null, actor)
        val buildingA = service.createBuilding(a.id, "MAIN", "Main", actor)
        val buildingB = service.createBuilding(b.id, "MAIN", "Main", actor)
        val floorA = service.createFloor(a.id, buildingA.id, 1, null, actor)
        val floorB = service.createFloor(b.id, buildingB.id, 1, null, actor)
        service.createRoom(a.id, buildingA.id, floorA.id, "101", "STANDARD", actor)
        service.createRoom(b.id, buildingB.id, floorB.id, "101", "STANDARD", actor)

        assertEquals(1,service.rooms(a.id,null,null,null,null,null,0,100).totalItems)
        assertEquals(1,service.rooms(a.id,"101",buildingA.id,floorA.id,"standard",true,0,100).totalItems)
        assertEquals(0,service.rooms(a.id,null,buildingB.id,null,null,null,0,100).totalItems)
        assertEquals(1,service.rooms(b.id,null,null,null,null,null,0,100).totalItems)
        assertEquals(listOf(floorA.id),service.floors(a.id,null).map { it.id })
        assertEquals(listOf(floorA.id),service.floors(a.id,buildingA.id).map { it.id })
        assertTrue(service.floors(a.id,buildingB.id).isEmpty())

        assertThrows<MasterDataConflict> { service.createRoom(a.id, buildingA.id, floorA.id, "101", null, actor) }
        assertThrows<MasterDataNotFound> { service.createRoom(a.id, buildingB.id, floorB.id, "102", null, actor) }
        service.updateHotel(a.id, a.name, a.timezone, null, false, actor)
        assertThrows<InactiveHotel> { service.createBuilding(a.id, "ANNEX", "Annex", actor) }
    }

    @Test
    fun `empty hotel returns an empty room page`() {
        val actor=existingUser()
        val hotel=service.createHotel("MH_EMPTY","Empty Hotel","UTC",null,actor)
        val page=service.rooms(hotel.id,null,null,null,null,null,0,100)
        assertTrue(page.items.isEmpty())
        assertEquals(0,page.totalItems)
    }

    @Test
    fun `default admin list requests are safe for every hotel scoped section`() {
        val actor=existingUser()
        val hotel=service.createHotel("MH_LISTS","List Hotel","UTC",null,actor)

        assertTrue(service.hotelsFor(actor,true).any { it.id==hotel.id })
        assertTrue(service.namedList("department",hotel.id).isEmpty())
        assertTrue(service.buildings(hotel.id).isEmpty())
        assertTrue(service.floors(hotel.id,null).isEmpty())
        assertTrue(service.rooms(hotel.id,null,null,null,null,null,0,25).items.isEmpty())
        assertTrue(service.memberships(hotel.id).isEmpty())
        assertTrue(service.namedList("role",hotel.id).isEmpty())
        assertTrue(service.namedList("skill",hotel.id).isEmpty())
        assertTrue(service.shifts(hotel.id).isEmpty())
        assertTrue(service.shiftAssignments(hotel.id,null,null,null).isEmpty())
    }

    @Test
    fun `one user can hold distinct hotel roles skills and shifts`() {
        val user = existingUser()
        val a = service.createHotel("MH_C", "Hotel C", "UTC", null, user)
        val b = service.createHotel("MH_D", "Hotel D", "UTC", null, user)
        val membershipA = service.addMembership(a.id, user, null, user)
        val membershipB = service.addMembership(b.id, user, null, user)
        val roleA = service.createNamed("role", a.id, "TECHNICIAN", "Technician", null, user)
        val roleB = service.createNamed("role", b.id, "SUPERVISOR", "Supervisor", null, user)
        service.assignRole(a.id, membershipA.id, roleA.id, user)
        service.assignRole(b.id, membershipB.id, roleB.id, user)
        val skill = service.createNamed("skill", a.id, "HVAC", "HVAC", null, user)
        service.assignSkill(a.id, membershipA.id, skill.id, "EXPERT", user)
        val shift = service.createShift(a.id, "NIGHT", "Night", LocalTime.of(23,0), LocalTime.of(7,0), user)
        assertTrue(shift.crossesMidnight)
        service.assignShift(a.id, membershipA.id, shift.id, LocalDate.of(2026,8,27), user)

        val updated = service.updateMembership(a.id, membershipA.id, "Hotel A Name", null, false, user)
        assertFalse(updated.membership.active)
        assertTrue(service.membershipDetail(b.id, membershipB.id).membership.active)

        assertEquals(1, service.memberships(a.id).size)
        assertEquals(1, service.memberships(b.id).size)
        assertThrows<MasterDataConflict> { service.assignRole(a.id, membershipA.id, roleA.id, user) }
        assertThrows<MasterDataNotFound> { service.assignSkill(b.id, membershipB.id, skill.id, null, user) }
    }

    @Test
    fun `onboarding persists skills and midnight shifts in the same transaction`() {
        val user=existingUser()
        val result=service.onboard(HotelOnboardingCommand("MH_FULL","Full Hotel","Europe/Berlin",null,listOf(OnboardingDepartment("OPS","Operations")),listOf(OnboardingBuilding("MAIN","Main",listOf(1))),listOf(OnboardingRoom("MAIN",1,"101","STANDARD")),listOf(OnboardingSkill("HVAC","HVAC",null)),listOf(OnboardingShift("NIGHT","Night",LocalTime.of(23,0),LocalTime.of(7,0))),user,null),user)
        assertEquals(1,result.skillCount)
        assertEquals(1,result.shiftCount)
        assertTrue(service.shifts(result.hotel.id).single().crossesMidnight)
    }

    @Test
    fun `onboarding rolls back every resource when a later step fails`() {
        val user = existingUser()
        assertThrows<IllegalArgumentException> {
            service.onboard(HotelOnboardingCommand("MH_ROLLBACK","Rollback","UTC",null,emptyList(),emptyList(),listOf(OnboardingRoom("MISSING",1,"101",null)),emptyList(),emptyList(),user,null),user)
        }
        val count=jdbc.queryForObject("select count(*) from hotel where code='MH_ROLLBACK'", emptyMap<String,Any>(),Long::class.java)
        assertEquals(0, count)
    }

    private fun existingUser(): UUID = jdbc.queryForObject("select id from app_user order by created_at limit 1", emptyMap<String,Any>(), UUID::class.java)!!
}
