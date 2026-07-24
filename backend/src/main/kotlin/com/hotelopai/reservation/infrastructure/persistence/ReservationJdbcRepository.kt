package com.hotelopai.reservation.infrastructure.persistence

import com.hotelopai.reservation.application.ReservationRepository
import com.hotelopai.reservation.application.ReservationSnapshot
import com.hotelopai.reservation.domain.DateRange
import com.hotelopai.reservation.domain.ExternalReservationReference
import com.hotelopai.reservation.domain.Guest
import com.hotelopai.reservation.domain.GuestId
import com.hotelopai.reservation.domain.Occupancy
import com.hotelopai.reservation.domain.PropertyId
import com.hotelopai.reservation.domain.Reservation
import com.hotelopai.reservation.domain.ReservationId
import com.hotelopai.reservation.domain.ReservationSource
import com.hotelopai.reservation.domain.ReservationStatus
import com.hotelopai.reservation.domain.RoomAssignment
import com.hotelopai.reservation.domain.RoomId
import com.hotelopai.reservation.domain.StayStatus
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
@Transactional
class ReservationJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ReservationRepository {
    @Transactional(readOnly = true)
    override fun findById(id: ReservationId): Reservation? =
        findSnapshotById(id)?.reservation

    @Transactional(readOnly = true)
    override fun findSnapshotById(id: ReservationId): ReservationSnapshot? =
        loadSnapshots(
            "where id = :id",
            mapOf("id" to id.value)
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun findByExternalReference(reference: ExternalReservationReference): Reservation? =
        loadSnapshots(
            "where external_reservation_reference = :reference",
            mapOf("reference" to reference.value)
        ).firstOrNull()?.reservation

    @Transactional(readOnly = true)
    override fun findSnapshotByMatch(
        providerId: String,
        externalReference: ExternalReservationReference,
        propertyId: PropertyId
    ): ReservationSnapshot? =
        loadSnapshots(
            """
            where provider_id = :providerId
              and external_reservation_reference = :externalReference
              and property_reference = :propertyReference
            """.trimIndent(),
            mapOf(
                "providerId" to providerId,
                "externalReference" to externalReference.value,
                "propertyReference" to propertyId.value
            )
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun findByPropertyAndDateRange(propertyId: PropertyId, dateRange: DateRange): List<Reservation> =
        loadSnapshots(
            """
            where property_reference = :propertyReference
              and arrival_date < :departure
              and departure_date > :arrival
            order by arrival_date asc, external_reservation_reference asc
            """.trimIndent(),
            mapOf(
                "propertyReference" to propertyId.value,
                "arrival" to dateRange.arrival,
                "departure" to dateRange.departure
            )
        ).map { it.reservation }

    override fun save(reservation: Reservation): Reservation =
        saveSnapshot(ReservationSnapshot("local", reservation)).reservation

    override fun saveSnapshot(snapshot: ReservationSnapshot): ReservationSnapshot {
        val existing = findSnapshotById(snapshot.reservation.id)
        return if (existing == null) {
            insert(snapshot)
        } else {
            update(snapshot, expectedVersion = snapshot.localVersion)
        }
    }

    private fun insert(snapshot: ReservationSnapshot): ReservationSnapshot {
        val normalized = snapshot.normalized()
        jdbcTemplate.update(
            """
            insert into reservation_snapshot (
                id, provider_id, external_reservation_reference, property_reference,
                reservation_status, stay_status, arrival_date, departure_date,
                occupancy_adults, occupancy_children, source, operational_notes,
                pms_source_updated_at, created_at, updated_at, version
            ) values (
                :id, :providerId, :externalReference, :propertyReference,
                :reservationStatus, :stayStatus, :arrivalDate, :departureDate,
                :occupancyAdults, :occupancyChildren, :source, :operationalNotes,
                :pmsSourceUpdatedAt, :createdAt, :updatedAt, :version
            )
            """.trimIndent(),
            normalized.toReservationParams()
        )
        replaceChildren(normalized)
        return normalized
    }

    private fun update(snapshot: ReservationSnapshot, expectedVersion: Long): ReservationSnapshot {
        val normalized = snapshot.normalized().copy(localVersion = expectedVersion + 1)
        val updated = jdbcTemplate.update(
            """
            update reservation_snapshot
            set provider_id = :providerId,
                external_reservation_reference = :externalReference,
                property_reference = :propertyReference,
                reservation_status = :reservationStatus,
                stay_status = :stayStatus,
                arrival_date = :arrivalDate,
                departure_date = :departureDate,
                occupancy_adults = :occupancyAdults,
                occupancy_children = :occupancyChildren,
                source = :source,
                operational_notes = :operationalNotes,
                pms_source_updated_at = :pmsSourceUpdatedAt,
                updated_at = :updatedAt,
                version = :version
            where id = :id
              and version = :expectedVersion
            """.trimIndent(),
            normalized.toReservationParams().addValue("expectedVersion", expectedVersion)
        )
        if (updated != 1) {
            throw OptimisticLockingFailureException("Reservation snapshot was modified concurrently.")
        }
        replaceChildren(normalized)
        return normalized
    }

    private fun replaceChildren(snapshot: ReservationSnapshot) {
        jdbcTemplate.update(
            "delete from reservation_guest_snapshot where reservation_id = :reservationId",
            mapOf("reservationId" to snapshot.reservation.id.value)
        )
        val guests = listOf(snapshot.reservation.primaryGuest to "PRIMARY") +
            snapshot.reservation.accompanyingGuests.map { it to "ACCOMPANYING" }
        guests.forEachIndexed { index, (guest, role) ->
            jdbcTemplate.update(
                """
                insert into reservation_guest_snapshot (
                    reservation_id, guest_id, guest_role, guest_order, created_at
                ) values (
                    :reservationId, :guestId, :guestRole, :guestOrder, :createdAt
                )
                """.trimIndent(),
                mapOf(
                    "reservationId" to snapshot.reservation.id.value,
                    "guestId" to guest.id.value,
                    "guestRole" to role,
                    "guestOrder" to index,
                    "createdAt" to snapshot.reservation.modifiedAt.toTimestamp()
                )
            )
        }

        jdbcTemplate.update(
            "delete from reservation_room_assignment_snapshot where reservation_id = :reservationId",
            mapOf("reservationId" to snapshot.reservation.id.value)
        )
        snapshot.reservation.roomAssignment?.let { assignment ->
            jdbcTemplate.update(
                """
                insert into reservation_room_assignment_snapshot (
                    reservation_id, room_id, assignment_arrival_date, assignment_departure_date, created_at
                ) values (
                    :reservationId, :roomId, :arrivalDate, :departureDate, :createdAt
                )
                """.trimIndent(),
                mapOf(
                    "reservationId" to snapshot.reservation.id.value,
                    "roomId" to assignment.roomId.value,
                    "arrivalDate" to assignment.period.arrival,
                    "departureDate" to assignment.period.departure,
                    "createdAt" to snapshot.reservation.modifiedAt.toTimestamp()
                )
            )
        }
    }

    private fun loadSnapshots(whereClause: String, params: Map<String, Any>): List<ReservationSnapshot> {
        val rows = jdbcTemplate.query(
            "select * from reservation_snapshot $whereClause",
            params,
            ::mapRow
        )
        if (rows.isEmpty()) {
            return emptyList()
        }
        val ids = rows.map { it.id.value }
        val guests = loadGuests(ids)
        val assignments = loadAssignments(ids)
        return rows.map { row ->
            val orderedGuests = guests[row.id.value].orEmpty().sortedBy { it.order }
            val primary = orderedGuests.firstOrNull { it.role == "PRIMARY" }
                ?: error("Reservation snapshot has no primary guest.")
            val accompanying = orderedGuests.filter { it.role == "ACCOMPANYING" }
            ReservationSnapshot(
                providerId = row.providerId,
                reservation = Reservation.create(
                    id = row.id,
                    externalReference = row.externalReference,
                    propertyId = row.propertyId,
                    primaryGuest = Guest(GuestId(primary.guestId)),
                    accompanyingGuests = accompanying.map { Guest(GuestId(it.guestId)) },
                    stayPeriod = row.stayPeriod,
                    reservationStatus = row.reservationStatus,
                    stayStatus = row.stayStatus,
                    roomAssignment = assignments[row.id.value],
                    occupancy = row.occupancy,
                    source = row.source,
                    operationalNotes = row.operationalNotes,
                    createdAt = row.createdAt,
                    modifiedAt = row.updatedAt
                ),
                pmsSourceUpdatedAt = row.pmsSourceUpdatedAt,
                localVersion = row.version
            )
        }
    }

    private fun loadGuests(ids: List<UUID>): Map<UUID, List<GuestRow>> =
        jdbcTemplate.query(
            """
            select *
            from reservation_guest_snapshot
            where reservation_id in (:ids)
            order by reservation_id, guest_order
            """.trimIndent(),
            mapOf("ids" to ids),
            { rs, _ ->
                GuestRow(
                    reservationId = rs.getObject("reservation_id", UUID::class.java),
                    guestId = rs.getString("guest_id"),
                    role = rs.getString("guest_role"),
                    order = rs.getInt("guest_order")
                )
            }
        ).groupBy { it.reservationId }

    private fun loadAssignments(ids: List<UUID>): Map<UUID, RoomAssignment> =
        jdbcTemplate.query(
            """
            select *
            from reservation_room_assignment_snapshot
            where reservation_id in (:ids)
            """.trimIndent(),
            mapOf("ids" to ids),
            { rs, _ ->
                rs.getObject("reservation_id", UUID::class.java) to RoomAssignment(
                    roomId = RoomId(rs.getString("room_id")),
                    period = DateRange(
                        arrival = rs.getObject("assignment_arrival_date", LocalDate::class.java),
                        departure = rs.getObject("assignment_departure_date", LocalDate::class.java)
                    )
                )
            }
        ).toMap()

    private fun mapRow(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): ReservationRow =
        ReservationRow(
            id = ReservationId(rs.getObject("id", UUID::class.java)),
            providerId = rs.getString("provider_id"),
            externalReference = ExternalReservationReference(rs.getString("external_reservation_reference")),
            propertyId = PropertyId(rs.getString("property_reference")),
            reservationStatus = ReservationStatus.valueOf(rs.getString("reservation_status")),
            stayStatus = StayStatus.valueOf(rs.getString("stay_status")),
            stayPeriod = DateRange(
                arrival = rs.getObject("arrival_date", LocalDate::class.java),
                departure = rs.getObject("departure_date", LocalDate::class.java)
            ),
            occupancy = Occupancy(
                adults = rs.getInt("occupancy_adults"),
                children = rs.getInt("occupancy_children")
            ),
            source = ReservationSource.valueOf(rs.getString("source")),
            operationalNotes = rs.getString("operational_notes"),
            pmsSourceUpdatedAt = rs.getTimestamp("pms_source_updated_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun ReservationSnapshot.normalized(): ReservationSnapshot =
        copy(
            reservation = reservation.copyForPersistence(),
            pmsSourceUpdatedAt = PersistenceInstant.toPersistencePrecisionOrNull(pmsSourceUpdatedAt)
        )

    private fun Reservation.copyForPersistence(): Reservation =
        Reservation.create(
            id = id,
            externalReference = externalReference,
            propertyId = propertyId,
            primaryGuest = Guest(primaryGuest.id),
            accompanyingGuests = accompanyingGuests.map { Guest(it.id) },
            stayPeriod = stayPeriod,
            reservationStatus = reservationStatus,
            stayStatus = stayStatus,
            roomAssignment = roomAssignment,
            occupancy = occupancy,
            source = source,
            operationalNotes = operationalNotes,
            createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
            modifiedAt = PersistenceInstant.toPersistencePrecision(modifiedAt)
        )

    private fun ReservationSnapshot.toReservationParams(): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", reservation.id.value)
            .addValue("providerId", providerId)
            .addValue("externalReference", reservation.externalReference.value)
            .addValue("propertyReference", reservation.propertyId.value)
            .addValue("reservationStatus", reservation.reservationStatus.name)
            .addValue("stayStatus", reservation.stayStatus.name)
            .addValue("arrivalDate", reservation.stayPeriod.arrival)
            .addValue("departureDate", reservation.stayPeriod.departure)
            .addValue("occupancyAdults", reservation.occupancy.adults)
            .addValue("occupancyChildren", reservation.occupancy.children)
            .addValue("source", reservation.source.name)
            .addValue("operationalNotes", reservation.operationalNotes)
            .addValue("pmsSourceUpdatedAt", pmsSourceUpdatedAt?.toTimestamp())
            .addValue("createdAt", reservation.createdAt.toTimestamp())
            .addValue("updatedAt", reservation.modifiedAt.toTimestamp())
            .addValue("version", localVersion)

    private fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)

    private data class ReservationRow(
        val id: ReservationId,
        val providerId: String,
        val externalReference: ExternalReservationReference,
        val propertyId: PropertyId,
        val reservationStatus: ReservationStatus,
        val stayStatus: StayStatus,
        val stayPeriod: DateRange,
        val occupancy: Occupancy,
        val source: ReservationSource,
        val operationalNotes: String?,
        val pmsSourceUpdatedAt: Instant?,
        val createdAt: Instant,
        val updatedAt: Instant,
        val version: Long
    )

    private data class GuestRow(
        val reservationId: UUID,
        val guestId: String,
        val role: String,
        val order: Int
    )
}
