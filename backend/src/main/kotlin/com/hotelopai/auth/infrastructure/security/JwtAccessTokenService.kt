package com.hotelopai.auth.infrastructure.security

import com.hotelopai.auth.application.AccessTokenContext
import com.hotelopai.auth.application.AccessTokenResult
import com.hotelopai.auth.application.AccessTokenService
import com.hotelopai.shared.kernel.UuidV7Generator
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
class JwtAccessTokenService(
    private val jwtEncoder: JwtEncoder,
    private val authSecurityProperties: AuthSecurityProperties
) : AccessTokenService {
    override fun issueToken(context: AccessTokenContext, now: Instant): AccessTokenResult {
        val jti = UuidV7Generator.generate(now).toString()
        val expiresAt = now.plus(authSecurityProperties.jwt.accessTokenTtl)
        val claimsBuilder = JwtClaimsSet.builder()
            .issuer(authSecurityProperties.jwt.issuer)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(context.userId.toString())
            .id(jti)
            .claim("token_type", "access")
            .claim("sid", context.sessionId.toString())
            .claim("hotelId", context.hotelId.toString())
            .claim("hotelName", context.hotelName)
            .claim("email", context.email)
            .claim("displayName", context.displayName)
            .claim("roleIds", context.roleIds.map(UUID::toString))
            .claim("roleCodes", context.roleCodes.toList())
            .claim("permissionIds", context.permissionIds.map(UUID::toString))
            .claim("permissions", context.permissionCodes.toList())
        context.employeeId?.let { claimsBuilder.claim("employeeId", it.toString()) }
        val claims = claimsBuilder.build()

        return AccessTokenResult(
            token = jwtEncoder.encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims
                )
            ).tokenValue,
            expiresAt = expiresAt,
            jti = jti
        )
    }
}

@Configuration
class JwtEncoderConfiguration(
    private val authSecurityProperties: AuthSecurityProperties,
    private val clock: Clock
) {
    @Bean
    fun jwtEncoder(): JwtEncoder {
        val secretKey = javax.crypto.spec.SecretKeySpec(
            authSecurityProperties.jwt.secret.toByteArray(Charsets.UTF_8),
            "HmacSHA256"
        )
        return org.springframework.security.oauth2.jwt.NimbusJwtEncoder(ImmutableSecret(secretKey))
    }

    @Bean
    fun jwtDecoder(): org.springframework.security.oauth2.jwt.JwtDecoder {
        val secretKey = javax.crypto.spec.SecretKeySpec(
            authSecurityProperties.jwt.secret.toByteArray(Charsets.UTF_8),
            "HmacSHA256"
        )
        return org.springframework.security.oauth2.jwt.NimbusJwtDecoder
            .withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .also {
                val timestampValidator = JwtTimestampValidator().apply {
                    setClock(clock)
                }
                it.setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        JwtIssuerValidator(authSecurityProperties.jwt.issuer),
                        timestampValidator
                    )
                )
            }
    }
}
