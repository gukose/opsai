package com.hotelopai.auth.api

import com.hotelopai.auth.application.AuthException
import com.hotelopai.auth.application.ExpiredRefreshTokenException
import com.hotelopai.auth.application.InvalidAccessSessionException
import com.hotelopai.auth.application.InvalidCredentialsException
import com.hotelopai.auth.application.InvalidRefreshTokenException
import com.hotelopai.auth.application.RevokedRefreshTokenException
import com.hotelopai.auth.application.UserInactiveException
import com.hotelopai.shared.error.ProblemDetailFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice(assignableTypes = [AuthController::class])
class AuthApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ProblemDetail {
        val fieldErrors = exception.bindingResult.fieldErrors.map { fieldError ->
            mapOf(
                "field" to fieldError.field,
                "message" to (fieldError.defaultMessage ?: "Invalid value")
            )
        }
        return ProblemDetailFactory.create(
            status = HttpStatus.BAD_REQUEST,
            title = "Validation failed",
            detail = "One or more fields are invalid",
            type = URI.create("https://hotelopai.com/problems/validation-error")
        ).apply {
            setProperty("errors", fieldErrors)
        }
    }

    @ExceptionHandler(
        InvalidCredentialsException::class,
        InvalidRefreshTokenException::class,
        ExpiredRefreshTokenException::class,
        RevokedRefreshTokenException::class,
        InvalidAccessSessionException::class
    )
    fun handleUnauthorized(exception: AuthException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.UNAUTHORIZED,
            title = problemTitle(exception),
            detail = exception.message ?: "Unauthorized",
            type = URI.create("https://hotelopai.com/problems/${problemType(exception)}")
        )

    @ExceptionHandler(UserInactiveException::class)
    fun handleInactive(exception: UserInactiveException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.FORBIDDEN,
            title = "User disabled",
            detail = exception.message ?: "User is disabled",
            type = URI.create("https://hotelopai.com/problems/user-disabled")
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(exception: AccessDeniedException): ProblemDetail =
        ProblemDetailFactory.create(
            status = HttpStatus.FORBIDDEN,
            title = "Forbidden",
            detail = exception.message ?: "You do not have access to this resource",
            type = URI.create("https://hotelopai.com/problems/forbidden")
        )

    private fun problemTitle(exception: AuthException): String =
        when (exception) {
            is InvalidCredentialsException -> "Invalid credentials"
            is InvalidRefreshTokenException -> "Invalid refresh token"
            is ExpiredRefreshTokenException -> "Expired refresh token"
            is RevokedRefreshTokenException -> "Revoked refresh token"
            is InvalidAccessSessionException -> "Invalid access session"
            else -> "Authentication error"
        }

    private fun problemType(exception: AuthException): String =
        when (exception) {
            is InvalidCredentialsException -> "invalid-credentials"
            is InvalidRefreshTokenException -> "invalid-refresh-token"
            is ExpiredRefreshTokenException -> "expired-refresh-token"
            is RevokedRefreshTokenException -> "revoked-refresh-token"
            is InvalidAccessSessionException -> "invalid-access-session"
            else -> "authentication-error"
        }
}
