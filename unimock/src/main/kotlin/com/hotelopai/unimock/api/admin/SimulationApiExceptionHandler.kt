package com.hotelopai.unimock.api.admin

import com.hotelopai.unimock.application.simulation.InvalidSimulationSeedException
import com.hotelopai.unimock.application.simulation.InvalidSimulationSeedPathException
import com.hotelopai.unimock.application.simulation.SimulationException
import com.hotelopai.unimock.application.simulation.SimulationNotLoadedException
import com.hotelopai.unimock.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice(assignableTypes = [SimulationAdminController::class])
class SimulationApiExceptionHandler {
    @ExceptionHandler(
        InvalidSimulationSeedPathException::class,
        InvalidSimulationSeedException::class
    )
    fun handleBadRequest(exception: SimulationException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid simulation seed",
            detail = exception.message ?: "Invalid simulation seed",
            type = URI.create("https://hotelopai.com/problems/unimock-seed-error")
        )

    @ExceptionHandler(SimulationNotLoadedException::class)
    fun handleNotFound(exception: SimulationNotLoadedException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "Simulation not loaded",
            detail = exception.message ?: "Simulation not loaded",
            type = URI.create("https://hotelopai.com/problems/unimock-simulation-not-loaded")
        )
}
