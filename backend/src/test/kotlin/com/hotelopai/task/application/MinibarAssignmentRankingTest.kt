package com.hotelopai.task.application

import com.hotelopai.employee.domain.Employee
import com.hotelopai.employee.domain.EmployeeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MinibarAssignmentRankingTest {
    private val hotelId=UUID.randomUUID()
    private val departmentId=UUID.randomUUID()
    private val minibarSkill=UUID.randomUUID()

    @Test fun `floor one housekeeper outranks lower workload floor three employee`() {
        val same=employee("E-1","1","HOUSEKEEPER")
        val other=employee("E-3","3","HOUSEKEEPER",setOf(minibarSkill))
        assertEquals(same.id,rank(listOf(candidate(same,100),candidate(other,300)),listOf(same,other),"1",mapOf(same.id to 3,other.id to 0)).first().employeeId)
    }

    @Test fun `floor three home area ranks first`() {
        val floorOne=employee("E-1","1","HOUSEKEEPER")
        val floorThree=employee("E-3","3","HOUSEKEEPER")
        assertEquals(floorThree.id,rank(listOf(candidate(floorOne,200),candidate(floorThree,100)),listOf(floorOne,floorThree),"3").first().employeeId)
    }

    @Test fun `ineligible same floor employee absent from eligible candidates allows fallback`() {
        val unavailable=employee("E-1","1","HOUSEKEEPER")
        val fallback=employee("E-2","2","HOUSEKEEPER")
        assertEquals(fallback.id,rank(listOf(candidate(fallback,100)),listOf(unavailable,fallback),"1").first().employeeId)
    }

    @Test fun `no same floor employee preserves existing score and workload fallback`() {
        val busy=employee("E-2","2","HOUSEKEEPER")
        val available=employee("E-3","3","HOUSEKEEPER")
        assertEquals(available.id,rank(listOf(candidate(busy,100),candidate(available,120)),listOf(busy,available),"1",mapOf(busy.id to 3,available.id to 0)).first().employeeId)
    }

    @Test fun `public area attendant does not outrank same floor housekeeper`() {
        val housekeeper=employee("E-1","1","HOUSEKEEPER")
        val publicArea=employee("E-PA","1","PUBLIC_AREA_ATTENDANT",setOf(minibarSkill))
        assertEquals(housekeeper.id,rank(listOf(candidate(housekeeper,100),candidate(publicArea,300)),listOf(housekeeper,publicArea),"1").first().employeeId)
    }

    private fun rank(candidates:List<AssignmentCandidate>,employees:List<Employee>,floor:String,workload:Map<UUID,Int> = emptyMap())=
        rankMinibarCandidates(candidates,employees,floor,minibarSkill,emptyMap(),workload)
    private fun candidate(employee:Employee,score:Int)=AssignmentCandidate(employee.id,employee.displayName,score,emptyList())
    private fun employee(number:String,homeArea:String?,role:String,skills:Set<UUID> = emptySet())=Employee(
        id=UUID.randomUUID(),hotelId=hotelId,employeeNumber=number,displayName=number,departmentId=departmentId,
        skillIds=skills,status=EmployeeStatus.ACTIVE,primaryRoleCode=role,homeArea=homeArea,createdAt=Instant.parse("2026-08-28T09:00:00Z")
    )
}
