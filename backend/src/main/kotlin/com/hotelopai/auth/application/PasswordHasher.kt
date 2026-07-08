package com.hotelopai.auth.application

interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(rawPassword: String, passwordHash: String): Boolean
}
