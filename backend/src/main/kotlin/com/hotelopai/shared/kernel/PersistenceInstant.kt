package com.hotelopai.shared.kernel

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * PostgreSQL stores timestamp values at microsecond precision. Normalize
 * values before returning persisted domain objects so save/reload equality is
 * deterministic across JVM and OS clock precision.
 */
fun Instant.toPersistencePrecision(): Instant = truncatedTo(ChronoUnit.MICROS)
