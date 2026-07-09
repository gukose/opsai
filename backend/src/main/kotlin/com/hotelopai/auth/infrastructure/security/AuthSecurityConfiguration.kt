package com.hotelopai.auth.infrastructure.security

import com.hotelopai.auth.application.AccessTokenService
import com.hotelopai.auth.application.AuthSessionPolicy
import com.hotelopai.auth.application.PasswordHasher
import com.hotelopai.auth.application.RefreshTokenCodec
import com.hotelopai.shared.error.ProblemDetailFactory
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.header.writers.StaticHeadersWriter
import java.time.Clock

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthSecurityProperties::class)
class AuthSecurityConfiguration(
    private val authSecurityProperties: AuthSecurityProperties
) {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun authSessionPolicy(): AuthSessionPolicy =
        object : AuthSessionPolicy {
            override fun refreshTokenTtl() = authSecurityProperties.jwt.refreshTokenTtl
        }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        authenticationEntryPoint: AuthenticationEntryPoint,
        accessDeniedHandler: AccessDeniedHandler
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .headers {
                it.contentTypeOptions { }
                it.frameOptions { frameOptions -> frameOptions.deny() }
                it.referrerPolicy { referrerPolicy ->
                    referrerPolicy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)
                }
                it.contentSecurityPolicy { contentSecurityPolicy ->
                    contentSecurityPolicy.policyDirectives(
                        "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'"
                    )
                }
                it.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true).maxAgeInSeconds(31536000)
                }
                it.addHeaderWriter(
                    StaticHeadersWriter(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()"
                    )
                )
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info"
                ).permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt -> jwt.decoder(jwtDecoder) }
            }

        return http.build()
    }

    @Bean
    fun authenticationEntryPoint(objectMapper: ObjectMapper): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request: HttpServletRequest, response: HttpServletResponse, _: org.springframework.security.core.AuthenticationException ->
            writeProblem(
                request = request,
                response = response,
                objectMapper = objectMapper,
                status = HttpStatus.UNAUTHORIZED,
                title = "Unauthorized",
                detail = "Authentication is required"
            )
        }

    @Bean
    fun accessDeniedHandler(objectMapper: ObjectMapper): AccessDeniedHandler =
        AccessDeniedHandler { request: HttpServletRequest, response: HttpServletResponse, _: org.springframework.security.access.AccessDeniedException ->
            writeProblem(
                request = request,
                response = response,
                objectMapper = objectMapper,
                status = HttpStatus.FORBIDDEN,
                title = "Forbidden",
                detail = "You do not have access to this resource"
            )
        }

    private fun writeProblem(
        request: HttpServletRequest,
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        status: HttpStatus,
        title: String,
        detail: String
    ) {
        val problemDetail = ProblemDetailFactory.create(
            status = status,
            title = title,
            detail = detail,
            instance = java.net.URI.create(request.requestURI)
        )

        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.outputStream, problemDetail)
    }
}
