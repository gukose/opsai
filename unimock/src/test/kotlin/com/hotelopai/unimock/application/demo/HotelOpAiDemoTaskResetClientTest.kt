package com.hotelopai.unimock.application.demo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hotelopai.unimock.config.UniMockProperties
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HotelOpAiDemoTaskResetClientTest {
    @Test fun `status and reset use fixed hotel shared key and reset exactly once`() {
        val server=HttpServer.create(InetSocketAddress(0),0)
        val resetCalls=AtomicInteger();val seenKeys=mutableListOf<String?>()
        server.createContext("/api/v1/internal/demo/reset/tasks/status") { exchange ->
            seenKeys+=exchange.requestHeaders.getFirst("X-Demo-Pms-Key")
            respond(exchange,200,"{\"hotelCode\":\"hotel-opai-demo\",\"taskCount\":14,\"resetAvailable\":true}")
        }
        server.createContext("/api/v1/internal/demo/reset/tasks") { exchange ->
            resetCalls.incrementAndGet();seenKeys+=exchange.requestHeaders.getFirst("X-Demo-Pms-Key")
            respond(exchange,200,"{\"tasksDeleted\":14,\"relatedRecordsDeleted\":30,\"remainingTasks\":0}")
        }
        server.start()
        try {
            val client=client(server)
            assertEquals(DemoTaskResetStatus("hotel-opai-demo",14,true),client.status())
            assertEquals(DemoTaskResetResult("hotel-opai-demo",14,30,0),client.reset())
            assertEquals(1,resetCalls.get())
            assertEquals(listOf("test-shared-key-123","test-shared-key-123"),seenKeys)
        } finally { server.stop(0) }
    }

    @Test fun `reset failure is friendly`() {
        val server=HttpServer.create(InetSocketAddress(0),0)
        server.createContext("/api/v1/internal/demo/reset/tasks") { exchange ->respond(exchange,500,"{}") }
        server.start()
        try { assertEquals("Hotel OpAI could not reset demo tasks. Please try again.",assertThrows(DemoTaskResetException::class.java) { client(server).reset() }.message) } finally { server.stop(0) }
    }

    @Test fun `concurrent reset is rejected without a second upstream call`() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val calls=AtomicInteger()
        val port=object:DemoTaskResetPort {
            override fun status()=DemoTaskResetStatus("hotel-opai-demo",14)
            override fun reset():DemoTaskResetResult { calls.incrementAndGet();entered.countDown();release.await(2,TimeUnit.SECONDS);return DemoTaskResetResult("hotel-opai-demo",14,0,0) }
        }
        val service=DemoTaskResetService(properties("http://localhost"),port)
        val first=Thread { service.reset() }.apply { start() }
        entered.await(2,TimeUnit.SECONDS)
        assertThrows(DemoTaskResetInProgressException::class.java) { service.reset() }
        release.countDown();first.join()
        assertEquals(1,calls.get())
    }

    private fun client(server:HttpServer)=HotelOpAiDemoTaskResetClient(properties("http://localhost:${server.address.port}"),jacksonObjectMapper())
    private fun properties(url:String)=UniMockProperties(demoConsole=UniMockProperties.DemoConsole(url,"hotel-opai-demo","test-shared-key-123"))
    private fun respond(exchange:com.sun.net.httpserver.HttpExchange,status:Int,json:String) { val body=json.toByteArray();exchange.responseHeaders.add("Content-Type","application/json");exchange.sendResponseHeaders(status,body.size.toLong());exchange.responseBody.use { it.write(body) } }
}
