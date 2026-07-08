package com.hotelopai.unimock.api.admin

import com.hotelopai.unimock.application.simulation.SimulationAdministrationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/simulation")
class SimulationAdminController(
    private val simulationAdministrationService: SimulationAdministrationService
) {
    @PostMapping("/load")
    fun load(): SimulationResponse =
        SimulationResponse.from(simulationAdministrationService.load())

    @PostMapping("/reset")
    fun reset(): SimulationResponse =
        SimulationResponse.from(simulationAdministrationService.reset())

    @GetMapping("/current")
    fun current(): SimulationResponse =
        SimulationResponse.from(simulationAdministrationService.current())
}
