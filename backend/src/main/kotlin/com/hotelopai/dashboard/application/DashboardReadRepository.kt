package com.hotelopai.dashboard.application

import com.hotelopai.dashboard.domain.DashboardWindow
import java.time.Instant
import java.util.UUID

interface DashboardReadRepository {
    fun summarizeTasks(
        hotelId: UUID,
        window: DashboardWindow,
        now: Instant
    ): DashboardTaskSummary

    fun summarizeSla(
        hotelId: UUID,
        window: DashboardWindow,
        now: Instant
    ): DashboardSlaSummary

    fun summarizeNotifications(
        hotelId: UUID,
        userId: UUID,
        roleCodes: Set<String>
    ): DashboardNotificationSummary

    fun summarizeWorkload(hotelId: UUID): DashboardWorkloadSummary
}
