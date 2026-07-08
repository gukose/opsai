package com.hotelopai.integration.unimock.api

import com.hotelopai.integration.unimock.PmsIntegrationTimeoutException
import com.hotelopai.integration.unimock.PmsIntegrationUnavailableException
import com.hotelopai.integration.unimock.PmsResourceNotFoundException
import com.hotelopai.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.format.DateTimeParseException

@RestControllerAdvice(assignableTypes = [DevPmsController::class])
@Profile("local", "test")
class DevPmsApiExceptionHandler {
    @ExceptionHandler(PmsResourceNotFoundException::class)
    fun handleNotFound(exception: PmsResourceNotFoundException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "PMS resource not found",
            detail = exception.message ?: "PMS resource not found",
            type = URI.create("https://hotelopai.com/problems/dev-pms-resource-not-found")
        )

    @ExceptionHandler(PmsIntegrationTimeoutException::class, PmsIntegrationUnavailableException::class)
    fun handleUnavailable(exception: RuntimeException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            title = "UniMock unavailable",
            detail = exception.message ?: "UniMock is unavailable",
            type = URI.create("https://hotelopai.com/problems/dev-unimock-unavailable")
        )

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class, DateTimeParseException::class)
    fun handleBadRequest(exception: RuntimeException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid PMS request",
            detail = exception.message ?: "Invalid PMS request",
            type = URI.create("https://hotelopai.com/problems/dev-pms-invalid-request")
        )
}
