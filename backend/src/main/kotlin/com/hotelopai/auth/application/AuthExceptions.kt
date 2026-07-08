package com.hotelopai.auth.application

open class AuthException(message: String) : RuntimeException(message)

class InvalidCredentialsException : AuthException("Invalid credentials")

class UserInactiveException(message: String = "User is disabled") : AuthException(message)

class InvalidRefreshTokenException : AuthException("Invalid refresh token")

class ExpiredRefreshTokenException : AuthException("Refresh token has expired")

class RevokedRefreshTokenException : AuthException("Refresh token has been revoked")

class InvalidAccessSessionException : AuthException("Invalid access token session")
