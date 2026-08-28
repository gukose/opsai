package com.hotelopai.unimock.application.demo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.unimock.config.UniMockProperties
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class HotelOpAiDemoEventClientTest {
    @Test fun `rooms and event delivery send the shared demo key`() {
        val server=HttpServer.create(InetSocketAddress(0),0);val seen=mutableListOf<String?>()
        server.createContext("/api/v1/integrations/pms/unimock/demo-events/rooms"){ exchange -> seen+=exchange.requestHeaders.getFirst("X-Demo-Pms-Key");val body="[]".toByteArray();exchange.responseHeaders.add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.size.toLong());exchange.responseBody.use{it.write(body)}}
        server.createContext("/api/v1/integrations/pms/unimock/demo-events"){ exchange -> seen+=exchange.requestHeaders.getFirst("X-Demo-Pms-Key");val body="{\"eventId\":\"E-1\",\"roomNumber\":\"205\",\"eventType\":\"DIRTY\",\"occurredAt\":\"2026-08-28T10:00:00Z\",\"status\":\"PROCESSED\",\"duplicate\":false}".toByteArray();exchange.responseHeaders.add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.size.toLong());exchange.responseBody.use{it.write(body)}}
        server.start()
        try { val client=HotelOpAiDemoEventClient(UniMockProperties(demoConsole=UniMockProperties.DemoConsole("http://localhost:${server.address.port}","hotel-opai-demo","test-shared-key-123")),jacksonObjectMapper());client.rooms();client.deliver(mapOf("eventId" to "E-1"));assertEquals(listOf("test-shared-key-123","test-shared-key-123"),seen) } finally { server.stop(0) }
    }
}
