package com.hotelopai.notification.api

import com.hotelopai.notification.application.NotificationNotFoundException
import com.hotelopai.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [NotificationController::class])
class NotificationApiExceptionHandler {
    @ExceptionHandler(NotificationNotFoundException::class)
    fun handleNotFound(exception: NotificationNotFoundException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "Notification not found",
            detail = exception.message ?: "Notification was not found"
        )
}
