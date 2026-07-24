package com.hotelopai.shared.kernel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PersistenceInstantTest {
    @Test
    fun `nanoseconds are truncated to microseconds`() {
        assertThat(
            PersistenceInstant.toPersistencePrecision(
                Instant.parse("2026-07-17T12:34:56.123456789Z")
            )
        ).isEqualTo(Instant.parse("2026-07-17T12:34:56.123456Z"))
    }

    @Test
    fun `exact microsecond value is unchanged`() {
        val value = Instant.parse("2026-07-17T12:34:56.123456Z")

        assertThat(PersistenceInstant.toPersistencePrecision(value)).isEqualTo(value)
    }

    @Test
    fun `zero instant is unchanged`() {
        assertThat(PersistenceInstant.toPersistencePrecision(Instant.EPOCH)).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun `pre epoch instant is truncated to microseconds`() {
        assertThat(
            PersistenceInstant.toPersistencePrecision(
                Instant.parse("1969-12-31T23:59:59.123456789Z")
            )
        ).isEqualTo(Instant.parse("1969-12-31T23:59:59.123456Z"))
    }

    @Test
    fun `normalization is idempotent`() {
        val normalized = PersistenceInstant.toPersistencePrecision(
            Instant.parse("2026-07-17T12:34:56.123456789Z")
        )

        assertThat(PersistenceInstant.toPersistencePrecision(normalized)).isEqualTo(normalized)
    }

    @Test
    fun `now uses supplied clock and returns microsecond precision`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-17T12:34:56.987654321Z"),
            ZoneOffset.UTC
        )

        assertThat(PersistenceInstant.now(clock)).isEqualTo(
            Instant.parse("2026-07-17T12:34:56.987654Z")
        )
    }

    @Test
    fun `nullable helper preserves null and normalizes values`() {
        assertThat(PersistenceInstant.toPersistencePrecisionOrNull(null)).isNull()
        assertThat(
            PersistenceInstant.toPersistencePrecisionOrNull(
                Instant.parse("2026-07-17T12:34:56.000001999Z")
            )
        ).isEqualTo(Instant.parse("2026-07-17T12:34:56.000001Z"))
    }
}
