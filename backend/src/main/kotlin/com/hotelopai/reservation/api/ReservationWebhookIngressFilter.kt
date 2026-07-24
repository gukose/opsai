package com.hotelopai.reservation.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.reservation.application.ReservationWebhookProcessingService
import com.hotelopai.reservation.application.ReservationWebhookProperties
import com.hotelopai.reservation.application.ReservationWebhookRejectedException
import com.hotelopai.reservation.application.ReservationWebhookRequest
import com.hotelopai.shared.kernel.PersistenceInstant
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(ReservationWebhookProperties::class)
class ReservationWebhookIngressFilter(
    private val processingService: ReservationWebhookProcessingService,
    private val properties: ReservationWebhookProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || !WEBHOOK_PATH.matches(request.requestURI)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!properties.enabled) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "Reservation webhook ingestion is not enabled.")
            return
        }
        if (request.contentType?.substringBefore(";")?.trim()?.lowercase() != MediaType.APPLICATION_JSON_VALUE) {
            response.sendError(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Unsupported webhook content type.")
            return
        }
        val body = request.inputStream.readBytes()
        if (body.size > properties.maxRequestBytes) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Reservation webhook request is too large.")
            return
        }
        val providerId = requireNotNull(WEBHOOK_PATH.matchEntire(request.requestURI)).groupValues[1]
        try {
            val record = processingService.ingest(
                ReservationWebhookRequest(
                    providerId = providerId,
                    contentType = request.contentType,
                    queryToken = request.getParameter("token"),
                    headers = request.headerNames.asSequence().associateWith { request.getHeader(it) },
                    body = body,
                    receivedAt = PersistenceInstant.now(clock)
                )
            )
            response.status = HttpStatus.ACCEPTED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
            objectMapper.writeValue(response.outputStream, record.toAcceptedResponse())
        } catch (_: ReservationWebhookRejectedException) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Reservation webhook request was rejected.")
        }
    }

    companion object {
        private val WEBHOOK_PATH = Regex("^/api/v1/integrations/pms/([^/]+)/webhooks$")
    }
}
