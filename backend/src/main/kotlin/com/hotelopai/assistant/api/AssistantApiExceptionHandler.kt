package com.hotelopai.assistant.api

import com.hotelopai.assistant.application.AssistantAttachmentIdempotencyConflictException
import com.hotelopai.assistant.application.ConversationConcurrencyException
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

    @ExceptionHandler(ConversationConcurrencyException::class)
    fun handleConcurrencyConflict(exception: ConversationConcurrencyException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.CONFLICT,
            title = "Assistant conversation conflict",
            detail = exception.message ?: "Assistant conversation was modified by another request",
            type = URI.create("https://hotelopai.com/problems/assistant-conversation-conflict")
        )

    @ExceptionHandler(AssistantAttachmentIdempotencyConflictException::class)
    fun handleAttachmentIdempotencyConflict(exception: AssistantAttachmentIdempotencyConflictException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.CONFLICT,
            title = "Attachment registration conflict",
            detail = exception.message ?: "Attachment registration idempotency conflict",
            type = URI.create("https://hotelopai.com/problems/attachment-registration-conflict")
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
