package com.hotelopai.unimock.application.pms

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class CanonicalRoomStateStore {
    private val statuses = ConcurrentHashMap<String, String>()
    fun status(roomNumber: String): String? = statuses[roomNumber]
    fun set(roomNumber: String, status: String) { statuses[roomNumber] = status }
}
