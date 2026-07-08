package com.hotelopai.unimock.api.pms

import com.hotelopai.unimock.support.UnimockPostgresIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
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
class PmsUpdateApiIntegrationTest : UnimockPostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        jdbcTemplate.update("delete from unimock.pms_mock_verification_log")
        mockMvc.perform(post("/api/admin/simulation/load"))
            .andExpect(status().isOk)
    }

    @Test
    fun `room status update writes verification log`() {
        mockMvc.perform(
            post("/api/pms/rooms/101/status")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"status":"DIRTY"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationLogId").exists())
            .andExpect(jsonPath("$.entityId").value("101"))
            .andExpect(jsonPath("$.status").value("DIRTY"))

        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.pms_mock_verification_log", Long::class.java))
            .isEqualTo(1L)
    }

    @Test
    fun `guest request creation writes verification log`() {
        val response = mockMvc.perform(
            post("/api/pms/guest-requests")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"roomNumber":"101","description":"Need extra pillows"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationLogId").exists())
            .andExpect(jsonPath("$.entityId").exists())
            .andReturn()
            .response
            .contentAsString

        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.pms_mock_verification_log", Long::class.java))
            .isEqualTo(1L)
        assertThat(response).contains("GUEST_REQUEST_CREATE")
    }

    @Test
    fun `guest request status update writes verification log`() {
        val created = mockMvc.perform(
            post("/api/pms/guest-requests")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"roomNumber":"101","description":"Need extra pillows"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val guestRequestId = extractValue(created, "entityId")

        mockMvc.perform(
            post("/api/pms/guest-requests/$guestRequestId/status")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"status":"CLOSED"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationLogId").exists())
            .andExpect(jsonPath("$.status").value("CLOSED"))

        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.pms_mock_verification_log", Long::class.java))
            .isEqualTo(2L)
    }

    @Test
    fun `minibar update writes verification log`() {
        mockMvc.perform(
            post("/api/pms/minibar/updates")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "roomNumber":"101",
                      "items":[
                        {"sku":"MINI-010","name":"Sparkling Water","quantity":6}
                      ]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationLogId").exists())
            .andExpect(jsonPath("$.entityId").value("101"))

        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.pms_mock_verification_log", Long::class.java))
            .isEqualTo(1L)
    }

    @Test
    fun `maintenance update writes verification log`() {
        mockMvc.perform(
            post("/api/pms/maintenance/updates")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "roomNumber":"101",
                      "issueTypeCode":"MAINTENANCE_AC",
                      "description":"Air conditioner not cooling",
                      "status":"OPEN"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationLogId").exists())
            .andExpect(jsonPath("$.entityId").value("101"))

        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.pms_mock_verification_log", Long::class.java))
            .isEqualTo(1L)
    }

    @Test
    fun `event push writes verification log`() {
        mockMvc.perform(
            post("/api/pms/events")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"type":"ROOM_INSPECTED"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.verificationLogId").exists())
            .andExpect(jsonPath("$.entityId").exists())

        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.pms_mock_verification_log", Long::class.java))
            .isEqualTo(1L)
    }

    @Test
    fun `verification log listing returns persisted writes`() {
        mockMvc.perform(
            post("/api/pms/rooms/101/status")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"status":"DIRTY"}""")
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/pms/mock-updates/verification-log"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].operation").value("ROOM_STATUS_UPDATE"))
    }

    @Test
    fun `verification events listing returns event push writes`() {
        mockMvc.perform(
            post("/api/pms/events")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"type":"ROOM_INSPECTED"}""")
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/pms/mock-updates/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].operation").value("EVENT_PUSH"))
    }

    @Test
    fun `update without active simulation returns problem details`() {
        jdbcTemplate.update("delete from unimock.pms_mock_verification_log")
        jdbcTemplate.update("delete from unimock.simulation_document")
        jdbcTemplate.update("delete from unimock.simulation")

        mockMvc.perform(
            post("/api/pms/rooms/101/status")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"status":"DIRTY"}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("Simulation not loaded"))
    }

    @Test
    fun `update for missing entity returns problem details`() {
        mockMvc.perform(
            post("/api/pms/rooms/999/status")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"status":"DIRTY"}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.title").value("PMS resource not found"))
            .andExpect(jsonPath("$.detail").value("Room not found: 999"))
    }

    private fun extractValue(json: String, fieldName: String): String {
        val regex = """"$fieldName"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?: error("Could not find field '$fieldName' in response: $json")
    }
}
