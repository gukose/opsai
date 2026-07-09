package com.hotelopai.task.api

import com.hotelopai.task.application.TaskCompletionPolicyException
import com.hotelopai.task.application.TaskNotFoundException
import com.hotelopai.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice(assignableTypes = [TaskController::class])
class TaskApiExceptionHandler {
    @ExceptionHandler(TaskNotFoundException::class)
    fun handleNotFound(exception: TaskNotFoundException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "Task not found",
            detail = exception.message ?: "Task not found",
            type = URI.create("https://hotelopai.com/problems/task-not-found")
        )

    @ExceptionHandler(TaskCompletionPolicyException::class)
    fun handleCompletionFailure(exception: TaskCompletionPolicyException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            title = "Task completion unavailable",
            detail = exception.message ?: "Task completion failed",
            type = URI.create("https://hotelopai.com/problems/task-completion-unavailable")
        )

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(exception: RuntimeException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid task request",
            detail = exception.message ?: "Invalid task request",
            type = URI.create("https://hotelopai.com/problems/invalid-task-request")
        )
}
