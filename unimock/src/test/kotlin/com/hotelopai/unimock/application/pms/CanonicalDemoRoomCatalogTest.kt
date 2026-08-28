package com.hotelopai.unimock.application.pms

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CanonicalDemoRoomCatalogTest {
    @Test fun `catalog contains canonical rooms and rejects invalid room`() {
        assertEquals(100, CanonicalDemoRoomCatalog.roomNumbers.size)
        assertNotNull(CanonicalDemoRoomCatalog.room("205"))
        assertNull(CanonicalDemoRoomCatalog.room("999"))
    }
}
