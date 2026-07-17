package com.hotelopai.notification.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NotificationJpaRepository : JpaRepository<NotificationJpaEntity, UUID> {
    @Query(
        """
        select n
        from NotificationJpaEntity n
        where n.hotelId = :hotelId
          and (
            n.recipientUserId = :userId
            or n.recipientRoleCode in :roleCodes
          )
        order by n.createdAt desc
        """
    )
    fun findAccessible(
        @Param("hotelId") hotelId: UUID,
        @Param("userId") userId: UUID,
        @Param("roleCodes") roleCodes: Set<String>
    ): List<NotificationJpaEntity>

    @Query(
        """
        select n
        from NotificationJpaEntity n
        where n.hotelId = :hotelId
          and n.recipientUserId = :userId
        order by n.createdAt desc
        """
    )
    fun findAccessibleForUserOnly(
        @Param("hotelId") hotelId: UUID,
        @Param("userId") userId: UUID
    ): List<NotificationJpaEntity>

    fun findBySourceEventId(sourceEventId: UUID): NotificationJpaEntity?

    fun findBySourceTaskIdAndType(sourceTaskId: UUID, type: com.hotelopai.notification.domain.NotificationType): NotificationJpaEntity?

    fun countBySourceTaskId(sourceTaskId: UUID): Long

    fun countBySourceEventId(sourceEventId: UUID): Long
}
