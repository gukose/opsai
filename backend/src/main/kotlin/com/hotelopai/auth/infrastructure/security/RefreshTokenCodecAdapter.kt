package com.hotelopai.auth.infrastructure.security

import com.hotelopai.auth.application.RefreshTokenCodec
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Component
class RefreshTokenCodecAdapter : RefreshTokenCodec {
    private val secureRandom = SecureRandom()

    override fun generate(): String {
        val bytes = ByteArray(48)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(rawRefreshToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawRefreshToken.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
