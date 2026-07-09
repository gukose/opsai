package com.hotelopai.api.task

import com.hotelopai.application.task.TaskNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class TaskApiExceptionHandler {
    @ExceptionHandler(TaskNotFoundException::class)
    fun handleNotFound(exception: TaskNotFoundException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiErrorResponse(message = exception.message ?: "Task not found"))

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(exception: RuntimeException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest()
            .body(ApiErrorResponse(message = exception.message ?: "Invalid task request"))
}

data class ApiErrorResponse(
    val message: String
)
