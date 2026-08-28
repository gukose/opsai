package com.hotelopai.unimock.application.demo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DemoTargetUriTest {
    @Test fun `preserves host and port for rooms and events with or without trailing slash`() {
        assertEquals("http://backend.railway.internal:8080/api/v1/integrations/pms/unimock/demo-events/rooms",demoTargetUri("http://backend.railway.internal:8080/","/api/v1/integrations/pms/unimock/demo-events/rooms").toString())
        assertEquals("https://backend.example/api/v1/integrations/pms/unimock/demo-events",demoTargetUri("https://backend.example","/api/v1/integrations/pms/unimock/demo-events").toString())
    }
    @Test fun `rejects missing host`() { val error=assertThrows<DemoEventDeliveryException>{demoTargetUri("http://","/path")};assertEquals("Hotel OpAI target URL is invalid",error.message) }
}
