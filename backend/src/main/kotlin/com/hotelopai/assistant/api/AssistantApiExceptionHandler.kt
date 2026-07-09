package com.hotelopai.assistant.api

import com.hotelopai.assistant.application.ConversationNotFoundException
import com.hotelopai.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice(assignableTypes = [AssistantConversationController::class])
class AssistantApiExceptionHandler {
    @ExceptionHandler(ConversationNotFoundException::class)
    fun handleNotFound(exception: ConversationNotFoundException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "Conversation not found",
            detail = exception.message ?: "Conversation not found",
            type = URI.create("https://hotelopai.com/problems/conversation-not-found")
        )

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(exception: RuntimeException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid assistant request",
            detail = exception.message ?: "Invalid assistant request",
            type = URI.create("https://hotelopai.com/problems/invalid-assistant-request")
        )
}
