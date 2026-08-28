package com.hotelopai.unimock.application.demo

import com.hotelopai.unimock.config.UniMockProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PmsDemoConsoleServiceTest {
    private val history=mock(PmsDemoConsoleHistoryRepository::class.java)
    private val delivery=FakeDelivery()
    private val now=Instant.parse("2026-08-28T10:15:30Z")
    private val service=PmsDemoConsoleService(history,UniMockProperties(demoConsole=UniMockProperties.DemoConsole(hotelCode="DEMO")),delivery,Clock.fixed(now,ZoneOffset.UTC))

    @Test fun `DIRTY payload follows Hotel OpAI contract and records processed history`() {
        val result=DemoEventResult("EVENT-1","205",DemoEventType.DIRTY,now,"PROCESSED")
        delivery.result=result
        assertEquals(result,service.send(DemoEventRequest("EVENT-1","205",DemoEventType.DIRTY)))
        assertEquals("DEMO",delivery.payload?.get("hotelCode"))
        assertEquals("205",delivery.payload?.get("roomNumber"))
        assertEquals("DIRTY",delivery.payload?.get("eventType"))
        verify(history).record(DemoEventRequest("EVENT-1","205",DemoEventType.DIRTY),"EVENT-1",now,"PROCESSED",null)
    }

    @Test fun `ROOM_MOVE validates canonical destination room`() {
        val error=assertThrows<IllegalArgumentException>{service.send(DemoEventRequest("EVENT-2","205",DemoEventType.ROOM_MOVE,"999"))}
        assertEquals("Destination room does not exist",error.message)
        assertNull(delivery.payload)
    }

    @Test fun `failed delivery is recorded and exposed as business error`() {
        delivery.error=DemoEventDeliveryException("Hotel OpAI rejected the event")
        val error=assertThrows<DemoEventDeliveryException>{service.send(DemoEventRequest("EVENT-3","310",DemoEventType.OOO,reason="Leak"))}
        assertEquals("Hotel OpAI rejected the event",error.message)
        verify(history).record(DemoEventRequest("EVENT-3","310",DemoEventType.OOO,reason="Leak"),"EVENT-3",now,"FAILED","Hotel OpAI rejected the event")
    }

    @Test fun `invalid source room is rejected before delivery`() {
        assertThrows<IllegalArgumentException>{service.send(DemoEventRequest("EVENT-4","999",DemoEventType.CHECK_OUT))}
        assertNull(delivery.payload)
    }

    private class FakeDelivery:DemoEventDeliveryPort {
        var result:DemoEventResult?=null;var error:RuntimeException?=null;var payload:Map<String,Any?>?=null
        override fun deliver(payload:Map<String,Any?>):DemoEventResult { this.payload=payload;error?.let{throw it};return requireNotNull(result) }
        override fun rooms()=listOf(DemoRoom("205","CLEAN"),DemoRoom("207","CLEAN"),DemoRoom("310","CLEAN"))
    }
}
