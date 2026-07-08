package com.hotelopai.shared.kernel

import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

object UuidV7Generator {
    private val random = SecureRandom()
    private val lastTimestampMillis = AtomicLong(-1L)
    private val subSecondCounter = AtomicLong(random.nextLong() and 0xFFFL)

    fun generate(now: Instant = Instant.now()): UUID {
        val timestampMillis = now.toEpochMilli()
        val sequence = nextSequence(timestampMillis)
        val mostSignificantBits = buildMostSignificantBits(timestampMillis, sequence)
        val leastSignificantBits = buildLeastSignificantBits()

        return UUID(mostSignificantBits, leastSignificantBits)
    }

    private fun nextSequence(timestampMillis: Long): Long {
        synchronized(this) {
            return if (lastTimestampMillis.get() == timestampMillis) {
                (subSecondCounter.incrementAndGet() and 0xFFFL)
            } else {
                lastTimestampMillis.set(timestampMillis)
                val seeded = random.nextLong() and 0xFFFL
                subSecondCounter.set(seeded)
                seeded
            }
        }
    }

    private fun buildMostSignificantBits(
        timestampMillis: Long,
        sequence: Long
    ): Long {
        val unixTimestamp = timestampMillis and 0xFFFFFFFFFFFFL
        val version = 0x7L shl 12
        val sequenceBits = sequence and 0xFFFL
        return (unixTimestamp shl 16) or version or sequenceBits
    }

    private fun buildLeastSignificantBits(): Long {
        val randomBits = random.nextLong() and 0x3FFFFFFFFFFFFFFFL
        return randomBits or Long.MIN_VALUE
    }
}
