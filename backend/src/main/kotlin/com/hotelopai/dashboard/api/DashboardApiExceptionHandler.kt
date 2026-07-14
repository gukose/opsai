package com.hotelopai.dashboard.api

import com.hotelopai.dashboard.domain.UnsupportedDashboardRangeException
import com.hotelopai.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [DashboardController::class])
class DashboardApiExceptionHandler {
    @ExceptionHandler(UnsupportedDashboardRangeException::class)
    fun handleUnsupportedRange(exception: UnsupportedDashboardRangeException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid dashboard range",
            detail = exception.message ?: "Unsupported dashboard range"
        )
}
