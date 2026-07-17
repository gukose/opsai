package com.hotelopai.notification.api

import com.hotelopai.notification.application.NotificationService
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.security.access.prepost.PreAuthorize
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
    @PreAuthorize(PermissionExpressions.NOTIFICATION_READ)
    fun listNotifications(): List<NotificationResponse> =
        notificationService.listAccessible(currentUserContextResolver.current())
            .map(NotificationResponse::from)

    @PostMapping("/{notificationId}/read")
    @PreAuthorize(PermissionExpressions.NOTIFICATION_MARK_READ)
    fun markRead(@PathVariable notificationId: UUID): NotificationResponse =
        NotificationResponse.from(
            notificationService.markRead(
                notificationId = notificationId,
                currentUser = currentUserContextResolver.current()
            )
        )
}
