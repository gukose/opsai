package com.hotelopai.dashboard.application

import com.hotelopai.dashboard.domain.DashboardTimeRange
import com.hotelopai.shared.security.CurrentUserContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional(readOnly = true)
class DashboardSummaryService(
    private val dashboardReadRepository: DashboardReadRepository,
    private val clock: Clock
) {
    fun summary(range: DashboardTimeRange, currentUser: CurrentUserContext): DashboardSummary {
        val now = clock.instant()
        val window = range.window(now)

        return DashboardSummary(
            hotelId = currentUser.hotelId,
            range = range,
            generatedAt = now,
            tasks = dashboardReadRepository.summarizeTasks(currentUser.hotelId, window, now),
            sla = dashboardReadRepository.summarizeSla(currentUser.hotelId, window, now),
            notifications = dashboardReadRepository.summarizeNotifications(
                hotelId = currentUser.hotelId,
                userId = currentUser.userId,
                roleCodes = currentUser.roles
            ),
            workload = dashboardReadRepository.summarizeWorkload(currentUser.hotelId)
        )
    }
}
