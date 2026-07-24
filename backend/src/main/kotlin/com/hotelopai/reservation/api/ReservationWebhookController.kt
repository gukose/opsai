package com.hotelopai.reservation.api

import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.reservation.application.ReservationWebhookInboxRecord
import com.hotelopai.reservation.application.ReservationWebhookProcessingService
import com.hotelopai.reservation.application.ReservationWebhookProperties
import com.hotelopai.reservation.application.ReservationWebhookRejectedException
import com.hotelopai.reservation.application.ReservationWebhookRequest
import com.hotelopai.reservation.application.ReservationWebhookStatus
import com.hotelopai.shared.kernel.PersistenceInstant
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

@RestController
@RequestMapping("/api/v1/integrations/pms/{providerId}/webhooks")
@EnableConfigurationProperties(ReservationWebhookProperties::class)
class ReservationWebhookController(
    private val processingService: ReservationWebhookProcessingService,
    private val properties: ReservationWebhookProperties,
    private val clock: Clock
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun receive(
        @PathVariable providerId: String,
        @RequestParam(name = "token", required = false) token: String?,
        request: HttpServletRequest,
        @RequestBody body: ByteArray
    ): ReservationWebhookAcceptedResponse {
        if (!properties.enabled) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation webhook ingestion is not enabled.")
        }
        if (body.size > properties.maxRequestBytes) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Reservation webhook request is too large.")
        }
        return try {
            processingService.ingest(
                ReservationWebhookRequest(
                    providerId = providerId,
                    contentType = request.contentType,
                    queryToken = token,
                    headers = request.headerNames.asSequence().associateWith { request.getHeader(it) },
                    body = body,
                    receivedAt = PersistenceInstant.now(clock)
                )
            ).toAcceptedResponse()
        } catch (exception: ReservationWebhookRejectedException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation webhook request was rejected.", exception)
        }
    }
}

data class ReservationWebhookAcceptedResponse(
    val status: ReservationWebhookStatus,
    val failureCategory: PmsFailureCategory?
)

fun ReservationWebhookInboxRecord.toAcceptedResponse(): ReservationWebhookAcceptedResponse =
    ReservationWebhookAcceptedResponse(
        status = status,
        failureCategory = failureCategory
    )
