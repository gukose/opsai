package com.hotelopai.dashboard.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class DashboardTimeRange(val wireValue: String) {
    TODAY("today"),
    SHIFT("shift"),
    SEVEN_DAYS("7d");

    fun window(now: Instant): DashboardWindow =
        when (this) {
            TODAY -> {
                val start = LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)
                DashboardWindow(startInclusive = start, endExclusive = now)
            }
            SHIFT -> DashboardWindow(startInclusive = now.minusSeconds(8 * 60 * 60), endExclusive = now)
            SEVEN_DAYS -> DashboardWindow(startInclusive = now.minusSeconds(7 * 24 * 60 * 60), endExclusive = now)
        }

    companion object {
        fun parse(value: String?): DashboardTimeRange =
            when (value?.trim()?.lowercase().takeUnless { it.isNullOrBlank() } ?: TODAY.wireValue) {
                TODAY.wireValue -> TODAY
                SHIFT.wireValue -> SHIFT
                SEVEN_DAYS.wireValue -> SEVEN_DAYS
                else -> throw UnsupportedDashboardRangeException(value ?: "")
            }
    }
}

data class DashboardWindow(
    val startInclusive: Instant,
    val endExclusive: Instant
)

class UnsupportedDashboardRangeException(value: String) : RuntimeException(
    "Unsupported dashboard range: $value"
)
