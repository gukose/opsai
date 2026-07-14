package com.hotelopai.dashboard.infrastructure.persistence

import com.hotelopai.dashboard.application.DashboardNotificationSummary
import com.hotelopai.dashboard.application.DashboardReadRepository
import com.hotelopai.dashboard.application.DashboardRecentNotification
import com.hotelopai.dashboard.application.DashboardSlaSummary
import com.hotelopai.dashboard.application.DashboardTaskCreatedInRangeSummary
import com.hotelopai.dashboard.application.DashboardTaskCurrentSnapshotSummary
import com.hotelopai.dashboard.application.DashboardTaskStatusCounts
import com.hotelopai.dashboard.application.DashboardTaskSummary
import com.hotelopai.dashboard.application.DashboardWorkloadSummary
import com.hotelopai.dashboard.application.TaskCreatedInRangeReport
import com.hotelopai.dashboard.application.TaskCreatedInRangeSlaReport
import com.hotelopai.dashboard.application.TaskCurrentSnapshotReport
import com.hotelopai.dashboard.application.TaskCurrentSnapshotSlaReport
import com.hotelopai.dashboard.application.TaskReportBucket
import com.hotelopai.dashboard.application.TaskReportingSummary
import com.hotelopai.dashboard.application.TaskReportingWindow
import com.hotelopai.dashboard.domain.DashboardTimeRange
import com.hotelopai.dashboard.domain.DashboardWindow
import com.hotelopai.notification.domain.NotificationType
import com.hotelopai.task.domain.TaskIntentType
import com.hotelopai.task.domain.TaskPriority
import com.hotelopai.task.domain.TaskStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class DashboardJdbcReadRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : DashboardReadRepository {
    override fun summarizeTasks(
        hotelId: UUID,
        window: DashboardWindow,
        now: Instant
    ): DashboardTaskSummary {
        val createdInRange = jdbcTemplate.queryForList(
            """
            select status, count(*) as count
            from task
            where hotel_id = :hotelId
              and created_at >= :startInclusive
              and created_at < :endExclusive
            group by status
            """.trimIndent(),
            params(hotelId)
                .addValue("startInclusive", sqlTimestamp(window.startInclusive))
                .addValue("endExclusive", sqlTimestamp(window.endExclusive))
        )
        val currentSnapshot = jdbcTemplate.queryForList(
            """
            select status, count(*) as count
            from task
            where hotel_id = :hotelId
            group by status
            """.trimIndent(),
            params(hotelId)
        )
        val currentStatusCounts = statusCounts(currentSnapshot)
        val active = activeCount(currentStatusCounts)
        val completed = currentStatusCounts.completed
        val trackedTotal = active + completed

        return DashboardTaskSummary(
            createdInRange = DashboardTaskCreatedInRangeSummary(
                total = createdInRange.sumOf { count(it) },
                statusCounts = statusCounts(createdInRange)
            ),
            currentSnapshot = DashboardTaskCurrentSnapshotSummary(
                active = active,
                urgent = scalarLong(
                    """
                    select count(*)
                    from task
                    where hotel_id = :hotelId
                      and status not in ('COMPLETED', 'CANCELLED')
                      and priority in ('HIGH', 'URGENT')
                    """.trimIndent(),
                    params(hotelId)
                ),
                unassigned = scalarLong(
                    """
                    select count(*)
                    from task
                    where hotel_id = :hotelId
                      and status not in ('COMPLETED', 'CANCELLED')
                      and assignee_id is null
                    """.trimIndent(),
                    params(hotelId)
                ),
                completionPercent = if (trackedTotal == 0L) 0 else ((completed * 100) / trackedTotal).toInt(),
                statusCounts = currentStatusCounts
            )
        )
    }

    override fun summarizeSla(
        hotelId: UUID,
        window: DashboardWindow,
        now: Instant
    ): DashboardSlaSummary =
        DashboardSlaSummary(
            dueSoon = scalarLong(
                """
                select count(*)
                from task
                where hotel_id = :hotelId
                  and status not in ('COMPLETED', 'CANCELLED', 'OVERDUE')
                  and sla_deadline > :now
                  and sla_deadline <= :dueSoonEndsAt
                """.trimIndent(),
                params(hotelId)
                    .addValue("now", sqlTimestamp(now))
                    .addValue("dueSoonEndsAt", sqlTimestamp(now.plusSeconds(2 * 60 * 60)))
            ),
            overdue = scalarLong(
                """
                select count(*)
                from task
                where hotel_id = :hotelId
                  and status not in ('COMPLETED', 'CANCELLED')
                  and (status = 'OVERDUE' or sla_deadline <= :now)
                """.trimIndent(),
                params(hotelId).addValue("now", sqlTimestamp(now))
            ),
            breached = scalarLong(
                """
                select count(*)
                from task
                where hotel_id = :hotelId
                  and created_at >= :startInclusive
                  and created_at < :endExclusive
                  and status <> 'CANCELLED'
                  and (
                    (completed_at is not null and completed_at > sla_deadline)
                    or (completed_at is null and sla_deadline <= :now)
                  )
                """.trimIndent(),
                params(hotelId)
                    .addValue("startInclusive", sqlTimestamp(window.startInclusive))
                    .addValue("endExclusive", sqlTimestamp(window.endExclusive))
                    .addValue("now", sqlTimestamp(now))
            )
        )

    override fun summarizeNotifications(
        hotelId: UUID,
        userId: UUID,
        roleCodes: Set<String>
    ): DashboardNotificationSummary {
        val source = accessibleNotificationParams(hotelId, userId, roleCodes)
        val unread = scalarLong(
            """
            select count(*)
            from notifications
            where hotel_id = :hotelId
              and status = 'UNREAD'
              and ${source.accessPredicate}
            """.trimIndent(),
            source.params
        )
        val recent = jdbcTemplate.query(
            """
            select id, type, title, body, created_at, source_task_id
            from notifications
            where hotel_id = :hotelId
              and ${source.accessPredicate}
            order by created_at desc
            limit 5
            """.trimIndent(),
            source.params,
        ) { rs, _ -> recentNotification(rs) }

        return DashboardNotificationSummary(unread = unread, recent = recent)
    }

    override fun summarizeWorkload(hotelId: UUID): DashboardWorkloadSummary =
        DashboardWorkloadSummary(
            assignedToUser = scalarLong(
                """
                select count(*)
                from task
                where hotel_id = :hotelId
                  and status not in ('COMPLETED', 'CANCELLED')
                  and assignee_type = 'USER'
                """.trimIndent(),
                params(hotelId)
            ),
            assignedToRole = scalarLong(
                """
                select count(*)
                from task
                where hotel_id = :hotelId
                  and status not in ('COMPLETED', 'CANCELLED')
                  and assignee_type = 'TEAM'
                """.trimIndent(),
                params(hotelId)
            ),
            unassigned = scalarLong(
                """
                select count(*)
                from task
                where hotel_id = :hotelId
                  and status not in ('COMPLETED', 'CANCELLED')
                  and assignee_id is null
                """.trimIndent(),
                params(hotelId)
            )
        )

    override fun taskReport(
        hotelId: UUID,
        range: DashboardTimeRange,
        window: DashboardWindow,
        generatedAt: Instant
    ): TaskReportingSummary {
        val createdParams = params(hotelId)
            .addValue("startInclusive", sqlTimestamp(window.startInclusive))
            .addValue("endExclusive", sqlTimestamp(window.endExclusive))
            .addValue("generatedAt", sqlTimestamp(generatedAt))

        val createdByType = groupCounts(
            """
            select intent_type as bucket_key, count(*) as count
            from task
            where hotel_id = :hotelId
              and created_at >= :startInclusive
              and created_at < :endExclusive
            group by intent_type
            """.trimIndent(),
            createdParams
        )
        val createdByStatus = groupCounts(
            """
            select status as bucket_key, count(*) as count
            from task
            where hotel_id = :hotelId
              and created_at >= :startInclusive
              and created_at < :endExclusive
            group by status
            """.trimIndent(),
            createdParams
        )
        val createdByPriority = groupCounts(
            """
            select priority as bucket_key, count(*) as count
            from task
            where hotel_id = :hotelId
              and created_at >= :startInclusive
              and created_at < :endExclusive
            group by priority
            """.trimIndent(),
            createdParams
        )

        val currentParams = params(hotelId)
            .addValue("generatedAt", sqlTimestamp(generatedAt))
            .addValue("dueSoonEndsAt", sqlTimestamp(generatedAt.plusSeconds(2 * 60 * 60)))
        val currentByStatus = groupCounts(
            """
            select status as bucket_key, count(*) as count
            from task
            where hotel_id = :hotelId
              and status not in ('COMPLETED', 'CANCELLED')
            group by status
            """.trimIndent(),
            currentParams
        )
        val currentByPriority = groupCounts(
            """
            select priority as bucket_key, count(*) as count
            from task
            where hotel_id = :hotelId
              and status not in ('COMPLETED', 'CANCELLED')
            group by priority
            """.trimIndent(),
            currentParams
        )
        val openOverdue = scalarLong(
            """
            select count(*)
            from task
            where hotel_id = :hotelId
              and created_at >= :startInclusive
              and created_at < :endExclusive
              and status not in ('COMPLETED', 'CANCELLED')
              and sla_deadline is not null
              and (status = 'OVERDUE' or sla_deadline <= :generatedAt)
            """.trimIndent(),
            createdParams
        )
        val completedLate = scalarLong(
            """
            select count(*)
            from task
            where hotel_id = :hotelId
              and created_at >= :startInclusive
              and created_at < :endExclusive
              and completed_at is not null
              and sla_deadline is not null
              and completed_at > sla_deadline
            """.trimIndent(),
            createdParams
        )

        return TaskReportingSummary(
            hotelId = hotelId,
            range = range,
            generatedAt = generatedAt,
            window = TaskReportingWindow(
                startInclusive = window.startInclusive,
                endExclusive = window.endExclusive
            ),
            createdInRange = TaskCreatedInRangeReport(
                total = createdByType.values.sum(),
                byType = orderedBuckets(TaskIntentType.entries.map { it.name }, createdByType),
                byStatus = orderedBuckets(TaskStatus.entries.map { it.name }, createdByStatus),
                byPriority = orderedBuckets(TaskPriority.entries.map { it.name }, createdByPriority),
                sla = TaskCreatedInRangeSlaReport(
                    completedOnTime = scalarLong(
                        """
                        select count(*)
                        from task
                        where hotel_id = :hotelId
                          and created_at >= :startInclusive
                          and created_at < :endExclusive
                          and completed_at is not null
                          and sla_deadline is not null
                          and completed_at <= sla_deadline
                        """.trimIndent(),
                        createdParams
                    ),
                    completedLate = completedLate,
                    openWithinSla = scalarLong(
                        """
                        select count(*)
                        from task
                        where hotel_id = :hotelId
                          and created_at >= :startInclusive
                          and created_at < :endExclusive
                          and status not in ('COMPLETED', 'CANCELLED')
                          and sla_deadline is not null
                          and status <> 'OVERDUE'
                          and sla_deadline > :generatedAt
                        """.trimIndent(),
                        createdParams
                    ),
                    openOverdue = openOverdue,
                    cancelled = scalarLong(
                        """
                        select count(*)
                        from task
                        where hotel_id = :hotelId
                          and created_at >= :startInclusive
                          and created_at < :endExclusive
                          and status = 'CANCELLED'
                        """.trimIndent(),
                        createdParams
                    ),
                    breached = completedLate + openOverdue
                )
            ),
            currentSnapshot = TaskCurrentSnapshotReport(
                active = currentByStatus.values.sum(),
                byStatus = orderedBuckets(TaskStatus.entries.filterNot { it in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED) }.map { it.name }, currentByStatus),
                byPriority = orderedBuckets(TaskPriority.entries.map { it.name }, currentByPriority),
                sla = TaskCurrentSnapshotSlaReport(
                    dueSoon = scalarLong(
                        """
                        select count(*)
                        from task
                        where hotel_id = :hotelId
                          and status not in ('COMPLETED', 'CANCELLED')
                          and sla_deadline is not null
                          and sla_deadline > :generatedAt
                          and sla_deadline <= :dueSoonEndsAt
                        """.trimIndent(),
                        currentParams
                    ),
                    overdue = scalarLong(
                        """
                        select count(*)
                        from task
                        where hotel_id = :hotelId
                          and status not in ('COMPLETED', 'CANCELLED')
                          and sla_deadline is not null
                          and (status = 'OVERDUE' or sla_deadline <= :generatedAt)
                        """.trimIndent(),
                        currentParams
                    )
                )
            )
        )
    }

    private fun recentNotification(rs: ResultSet): DashboardRecentNotification =
        DashboardRecentNotification(
            id = rs.getObject("id", UUID::class.java),
            type = NotificationType.valueOf(rs.getString("type")),
            title = rs.getString("title"),
            body = rs.getString("body"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            sourceTaskId = rs.getObject("source_task_id", UUID::class.java)
        )

    private fun statusCounts(rows: List<Map<String, Any?>>): DashboardTaskStatusCounts =
        DashboardTaskStatusCounts(
            created = rows.countForStatus("CREATED"),
            assigned = rows.countForStatus("ASSIGNED"),
            started = rows.countForStatus("STARTED"),
            inProgress = rows.countForStatus("IN_PROGRESS"),
            waiting = rows.countForStatus("WAITING"),
            overdue = rows.countForStatus("OVERDUE"),
            completed = rows.countForStatus("COMPLETED"),
            cancelled = rows.countForStatus("CANCELLED")
        )

    private fun activeCount(counts: DashboardTaskStatusCounts): Long =
        counts.created + counts.assigned + counts.started + counts.inProgress + counts.waiting + counts.overdue

    private fun scalarLong(sql: String, params: MapSqlParameterSource): Long =
        jdbcTemplate.queryForObject(sql, params, Long::class.javaObjectType) ?: 0L

    private fun groupCounts(sql: String, params: MapSqlParameterSource): Map<String, Long> =
        jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getString("bucket_key") to rs.getLong("count")
        }.toMap()

    private fun orderedBuckets(knownOrder: List<String>, counts: Map<String, Long>): List<TaskReportBucket> {
        val known = knownOrder.map { TaskReportBucket(it, counts[it] ?: 0L) }
        val unknown = counts.keys
            .filterNot { it in knownOrder }
            .sorted()
            .map { TaskReportBucket(it, counts[it] ?: 0L) }
        return known + unknown
    }

    private fun params(hotelId: UUID): MapSqlParameterSource =
        MapSqlParameterSource("hotelId", hotelId)

    private fun sqlTimestamp(value: Instant): Timestamp =
        Timestamp.from(value)

    private fun accessibleNotificationParams(
        hotelId: UUID,
        userId: UUID,
        roleCodes: Set<String>
    ): AccessibleNotificationQuery {
        val params = params(hotelId).addValue("userId", userId)
        return if (roleCodes.isEmpty()) {
            AccessibleNotificationQuery(
                accessPredicate = "recipient_user_id = :userId",
                params = params
            )
        } else {
            AccessibleNotificationQuery(
                accessPredicate = "(recipient_user_id = :userId or recipient_role_code in (:roleCodes))",
                params = params.addValue("roleCodes", roleCodes)
            )
        }
    }

    private fun List<Map<String, Any?>>.countForStatus(status: String): Long =
        firstOrNull { it["status"] == status }?.let(::count) ?: 0L

    private fun count(row: Map<String, Any?>): Long =
        (row["count"] as Number).toLong()

    private data class AccessibleNotificationQuery(
        val accessPredicate: String,
        val params: MapSqlParameterSource
    )
}
