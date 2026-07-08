package com.hotelopai.shared.kernel

import java.util.UUID

object CorrelationIdContextHolder {
    private val current = ThreadLocal<String?>()

    fun set(correlationId: String) {
        current.set(correlationId)
    }

    fun current(): String? = current.get()

    fun currentOrCreate(): String {
        val existing = current.get()?.takeIf { it.isNotBlank() }
        if (existing != null) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        current.set(generated)
        return generated
    }

    fun clear() {
        current.remove()
    }
}
