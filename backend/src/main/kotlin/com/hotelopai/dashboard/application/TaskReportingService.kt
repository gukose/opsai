package com.hotelopai.dashboard.application

import com.hotelopai.dashboard.domain.DashboardTimeRange
import com.hotelopai.shared.security.CurrentUserContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional(readOnly = true)
class TaskReportingService(
    private val dashboardReadRepository: DashboardReadRepository,
    private val clock: Clock
) {
    fun taskReport(range: DashboardTimeRange, currentUser: CurrentUserContext): TaskReportingSummary {
        val generatedAt = clock.instant()
        val window = range.window(generatedAt)

        return dashboardReadRepository.taskReport(
            hotelId = currentUser.hotelId,
            range = range,
            window = window,
            generatedAt = generatedAt
        )
    }
}
