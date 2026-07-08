package com.hotelopai.integration.unimock

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UniMockErrorsTest {
    @Test
    fun maps404ToNotFound() {
        val error = UniMockErrorMapper.map(
            statusCode = 404,
            rawBody = """{"message":"Room not found"}""",
            fallbackMessage = "fallback"
        )

        assertIs<UniMockClientNotFoundException>(error)
        assertTrue(error.message!!.contains("Room not found"))
    }

    @Test
    fun mapsTimeoutTransportFailure() {
        val error = UniMockErrorMapper.mapTransportFailure(
            message = "timeout",
            cause = java.net.http.HttpTimeoutException("timeout")
        )

        assertIs<UniMockClientTimeoutException>(error)
    }
}
