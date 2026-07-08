package com.hotelopai.unimock.api.admin

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
class SimulationAdministrationIntegrationTest : UnimockPostgresIntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    @Test
    fun `load simulation persists active snapshot and documents`() {
        mockMvc.perform(post("/api/admin/simulation/load"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.simulationCode").value("grand-hotel"))
            .andExpect(jsonPath("$.simulationName").value("Grand Hotel"))
            .andExpect(jsonPath("$.documentCount").value(23))

        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.simulation", Long::class.java))
            .isEqualTo(1L)
        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.simulation_document", Long::class.java))
            .isEqualTo(23L)
    }

    @Test
    fun `reset simulation reloads deterministic snapshot`() {
        val firstResponse = mockMvc.perform(post("/api/admin/simulation/load"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val firstSimulationId = extractValue(firstResponse, "simulationId")

        val resetResponse = mockMvc.perform(post("/api/admin/simulation/reset"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val resetSimulationId = extractValue(resetResponse, "simulationId")

        assertThat(resetSimulationId).isEqualTo(firstSimulationId)
        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.simulation", Long::class.java))
            .isEqualTo(1L)
        assertThat(jdbcTemplate.queryForObject("select count(*) from unimock.simulation_document", Long::class.java))
            .isEqualTo(23L)
    }

    @Test
    fun `current simulation returns active metadata`() {
        mockMvc.perform(post("/api/admin/simulation/load"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/admin/simulation/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.simulationCode").value("grand-hotel"))
            .andExpect(jsonPath("$.simulationName").value("Grand Hotel"))
            .andExpect(jsonPath("$.seedPath").value("classpath:/simulation/grand-hotel"))
            .andExpect(jsonPath("$.documentCount").value(23))
    }

    private fun extractValue(json: String, fieldName: String): String {
        val regex = """"$fieldName"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?: error("Could not find field '$fieldName' in response: $json")
    }
}
