package com.hotelopai.demo

import com.hotelopai.shared.security.CurrentUserContextResolver
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Hidden
@Profile("!prod & (demo | local | test)")
@RequestMapping("/api/v1/internal/demo/reset/tasks")
@PreAuthorize("@permissionGuard.hasRole('ADMIN') or (authentication.name == 'pms-demo-system' and hasAuthority('ROLE_PMS_INTEGRATION'))")
class DemoTaskResetController(
    private val resetService: DemoTaskResetService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping
    fun resetTasks(): DemoTaskResetResult = if (isPmsDemoSystem()) {
        resetService.resetConfiguredDemoTasks()
    } else {
        resetService.resetTasks(currentUserContextResolver.current().hotelId)
    }

    @GetMapping("/status")
    fun status(): DemoTaskResetStatus = if (isPmsDemoSystem()) {
        resetService.configuredDemoStatus()
    } else {
        resetService.status(currentUserContextResolver.current().hotelId)
    }

    private fun isPmsDemoSystem(): Boolean =
        SecurityContextHolder.getContext().authentication?.name == "pms-demo-system"
}
