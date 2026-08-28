package com.hotelopai.unimock.api.demo

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PmsDemoConsoleStaticUiTest {
    @Test fun `console contains business controls explicit send and history`() {
        val html=requireNotNull(javaClass.getResource("/static/index.html")).readText()
        val script=requireNotNull(javaClass.getResource("/static/demo-console.js")).readText()
        assertTrue(html.contains("PMS Demo Console"))
        assertTrue(html.contains("Send PMS Event"))
        assertTrue(html.contains("Recent Events"))
        assertTrue(html.contains("Demo Tools"))
        assertTrue(html.contains("Reset Demo Tasks"))
        assertTrue(script.contains("This will delete all demo tasks and related task data for \${configuredHotel}. Continue?"))
        assertTrue(script.contains("fetch(url,options)"))
        assertTrue(!script.contains("Authorization"))
        assertTrue(!script.contains("X-Demo-Pms-Key"))
        assertTrue(script.contains("if(resetting)return"))
        assertTrue(script.contains("resetting?'Resetting...':'Reset Demo Tasks'"))
        assertTrue(script.contains("ROOM_MOVE"))
        assertTrue(script.contains("lastPayload&&submit(lastPayload)"))
        assertTrue(script.contains("roomsLoading=true"))
        assertTrue(script.contains("historyLoading=false"))
        assertTrue(script.contains("sending=false"))
        assertTrue(script.contains("finally{sending=false;updateSendState()}"))
        assertTrue(script.contains("send.textContent=sending?'Sending…':'Send PMS Event'"))
    }
}
