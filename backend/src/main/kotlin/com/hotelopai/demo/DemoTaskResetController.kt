package com.hotelopai.demo

import com.hotelopai.shared.security.CurrentUserContextResolver
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Hidden
@Profile("!prod & (demo | local | test)")
@RequestMapping("/api/v1/internal/demo/reset/tasks")
@PreAuthorize("@permissionGuard.hasRole('ADMIN')")
class DemoTaskResetController(
    private val resetService: DemoTaskResetService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping
    fun resetTasks(): DemoTaskResetResult = resetService.resetTasks(currentUserContextResolver.current().hotelId)

    @GetMapping("/status")
    fun status(): DemoTaskResetStatus = resetService.status(currentUserContextResolver.current().hotelId)
}
