package com.hotelopai.notification.api

import com.hotelopai.notification.application.NotificationService
import com.hotelopai.shared.security.CurrentUserContextResolver
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @GetMapping
    fun listNotifications(): List<NotificationResponse> =
        notificationService.listAccessible(currentUserContextResolver.current())
            .map(NotificationResponse::from)

    @PostMapping("/{notificationId}/read")
    fun markRead(@PathVariable notificationId: UUID): NotificationResponse =
        NotificationResponse.from(
            notificationService.markRead(
                notificationId = notificationId,
                currentUser = currentUserContextResolver.current()
            )
        )
}
