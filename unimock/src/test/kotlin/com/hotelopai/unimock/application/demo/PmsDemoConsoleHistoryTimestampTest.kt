package com.hotelopai.unimock.application.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class PmsDemoConsoleHistoryTimestampTest {
    @Test fun `Instant binding preserves UTC instant for timestamptz columns`() {
        val instant=Instant.parse("2026-08-28T10:15:30.123456Z")
        assertEquals(instant,historyTimestamp(instant).toInstant())
    }
}
