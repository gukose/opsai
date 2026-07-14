package com.hotelopai.dashboard.api

import com.hotelopai.dashboard.application.DashboardSummaryService
import com.hotelopai.dashboard.domain.DashboardTimeRange
import com.hotelopai.shared.security.CurrentUserContextResolver
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController(
    private val dashboardSummaryService: DashboardSummaryService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @GetMapping("/summary")
    fun summary(@RequestParam(required = false) range: String?): DashboardSummaryResponse =
        DashboardSummaryResponse.from(
            dashboardSummaryService.summary(
                range = DashboardTimeRange.parse(range),
                currentUser = currentUserContextResolver.current()
            )
        )
}
