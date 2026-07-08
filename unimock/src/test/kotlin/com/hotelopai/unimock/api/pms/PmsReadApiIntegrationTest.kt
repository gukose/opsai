package com.hotelopai.unimock.api.pms

import com.hotelopai.unimock.support.UnimockPostgresIntegrationTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.context.WebApplicationContext
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PmsReadApiIntegrationTest : UnimockPostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        mockMvc.perform(post("/api/admin/simulation/load"))
            .andExpect(status().isOk)
    }

    @Test
    fun `list rooms`() {
        mockMvc.perform(get("/api/pms/rooms"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].roomNumber").value("101"))
    }

    @Test
    fun `get room by room number`() {
        mockMvc.perform(get("/api/pms/rooms/101"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roomNumber").value("101"))
            .andExpect(jsonPath("$.roomTypeCode").value("STANDARD"))
    }

    @Test
    fun `get room status`() {
        mockMvc.perform(get("/api/pms/rooms/101/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roomNumber").value("101"))
            .andExpect(jsonPath("$.status").value("OCCUPIED"))
    }

    @Test
    fun `get occupancy`() {
        mockMvc.perform(get("/api/pms/rooms/101/occupancy"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roomNumber").value("101"))
            .andExpect(jsonPath("$.occupied").value(true))
    }

    @Test
    fun `get room assets`() {
        mockMvc.perform(get("/api/pms/rooms/101/assets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].roomNumber").value("101"))
    }

    @Test
    fun `get asset by id`() {
        mockMvc.perform(get("/api/pms/assets/ASSET-101-A1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assetId").value("ASSET-101-A1"))
            .andExpect(jsonPath("$.name").value("Television"))
    }

    @Test
    fun `list issue types`() {
        mockMvc.perform(get("/api/pms/issue-types"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `list public areas`() {
        mockMvc.perform(get("/api/pms/public-areas"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `list reservations`() {
        mockMvc.perform(get("/api/pms/reservations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].reservationId").value("RES-101"))
    }

    @Test
    fun `get reservation by id`() {
        mockMvc.perform(get("/api/pms/reservations/RES-101"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reservationId").value("RES-101"))
            .andExpect(jsonPath("$.guestId").value("GUEST-001"))
    }

    @Test
    fun `get guest by id`() {
        mockMvc.perform(get("/api/pms/guests/GUEST-001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.guestId").value("GUEST-001"))
            .andExpect(jsonPath("$.firstName").value("Anna"))
    }

    @Test
    fun `list events`() {
        mockMvc.perform(get("/api/pms/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `not found case returns problem details`() {
        mockMvc.perform(get("/api/pms/rooms/999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("PMS resource not found"))
            .andExpect(jsonPath("$.detail").value("Room not found: 999"))
    }

    @Test
    fun `no active simulation case returns problem details`() {
        jdbcTemplate.update("delete from unimock.simulation_document")
        jdbcTemplate.update("delete from unimock.simulation")

        mockMvc.perform(get("/api/pms/rooms"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Simulation not loaded"))
    }
}
