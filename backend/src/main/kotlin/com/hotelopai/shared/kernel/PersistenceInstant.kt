package com.hotelopai.shared.kernel

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * PostgreSQL stores timestamp values at microsecond precision while JVM
 * Instants can carry nanoseconds. Normalize values at persistence creation
 * boundaries so immediate-return, reload, and idempotent duplicate paths use
 * the same equality semantics.
 */
object PersistenceInstant {
    fun now(clock: Clock): Instant =
        toPersistencePrecision(clock.instant())

    fun toPersistencePrecision(instant: Instant): Instant =
        instant.truncatedTo(ChronoUnit.MICROS)

    fun toPersistencePrecisionOrNull(instant: Instant?): Instant? =
        instant?.let(::toPersistencePrecision)
}
