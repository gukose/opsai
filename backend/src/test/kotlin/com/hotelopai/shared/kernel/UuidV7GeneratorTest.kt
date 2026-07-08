package com.hotelopai.shared.kernel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class UuidV7GeneratorTest {
    @Test
    fun `generate returns version 7 uuids`() {
        val first = UuidV7Generator.generate(Instant.parse("2026-07-08T10:00:00Z"))
        val second = UuidV7Generator.generate(Instant.parse("2026-07-08T10:00:00Z"))

        assertEquals(7, first.version())
        assertEquals(2, first.variant())
        assertEquals(7, second.version())
        assertEquals(2, second.variant())
        assertNotEquals(first, second)
    }
}
