package com.hotelopai.unimock.api.pms

import com.hotelopai.unimock.application.pms.PmsReadException
import com.hotelopai.unimock.application.pms.PmsResourceNotFoundException
import com.hotelopai.unimock.application.simulation.SimulationNotLoadedException
import com.hotelopai.unimock.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice(basePackages = ["com.hotelopai.unimock.api.pms"])
class PmsReadApiExceptionHandler {
    @ExceptionHandler(SimulationNotLoadedException::class)
    fun handleSimulationNotLoaded(exception: SimulationNotLoadedException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "Simulation not loaded",
            detail = exception.message ?: "Simulation not loaded",
            type = URI.create("https://hotelopai.com/problems/unimock-simulation-not-loaded")
        )

    @ExceptionHandler(PmsResourceNotFoundException::class)
    fun handleNotFound(exception: PmsResourceNotFoundException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "PMS resource not found",
            detail = exception.message ?: "PMS resource not found",
            type = URI.create("https://hotelopai.com/problems/unimock-resource-not-found")
        )

    @ExceptionHandler(PmsReadException::class)
    fun handleBadRequest(exception: PmsReadException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid PMS request",
            detail = exception.message ?: "Invalid PMS request",
            type = URI.create("https://hotelopai.com/problems/unimock-pms-read-error")
        )
}
