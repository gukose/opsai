package com.hotelopai.auth.application

interface RefreshTokenCodec {
    fun generate(): String

    fun hash(rawRefreshToken: String): String
}
