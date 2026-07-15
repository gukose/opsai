package com.hotelopai.vision.api

import com.hotelopai.assistant.application.ConversationNotFoundException
import com.hotelopai.shared.error.ProblemDetailFactory
import com.hotelopai.vision.application.VisionAnalysisImportConflictException
import com.hotelopai.vision.application.VisionAnalysisImportNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice(assignableTypes = [VisionAnalysisImportController::class])
class VisionAnalysisImportApiExceptionHandler {
    @ExceptionHandler(ConversationNotFoundException::class, VisionAnalysisImportNotFoundException::class)
    fun handleNotFound(exception: RuntimeException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.NOT_FOUND,
            title = "Vision analysis not found",
            detail = exception.message ?: "Vision analysis not found",
            type = URI.create("https://hotelopai.com/problems/vision-analysis-not-found")
        )

    @ExceptionHandler(VisionAnalysisImportConflictException::class)
    fun handleConflict(exception: VisionAnalysisImportConflictException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.CONFLICT,
            title = "Vision analysis import conflict",
            detail = exception.message ?: "Vision analysis import conflict",
            type = URI.create("https://hotelopai.com/problems/vision-analysis-import-conflict")
        )

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(exception: RuntimeException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid vision analysis import request",
            detail = exception.message ?: "Invalid vision analysis import request",
            type = URI.create("https://hotelopai.com/problems/invalid-vision-analysis-import-request")
        )
}
