package com.hotelopai.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class OperationalObservability(
    private val meterRegistry: MeterRegistry? = null
) {
    private val gauges = ConcurrentHashMap<String, AtomicLong>()

    fun incrementCounter(name: String, vararg tags: Pair<String, String>) {
        incrementCounter(name, 1.0, *tags)
    }

    fun incrementCounter(name: String, amount: Double, vararg tags: Pair<String, String>) {
        val registry = meterRegistry ?: return
        runCatching {
            registry.counter(name, safeTags(tags)).increment(amount.coerceAtLeast(0.0))
        }
    }

    fun startTimer(): Timer.Sample? =
        meterRegistry?.let(Timer::start)

    fun stopTimer(sample: Timer.Sample?, name: String, vararg tags: Pair<String, String>) {
        val registry = meterRegistry ?: return
        if (sample == null) return
        runCatching {
            sample.stop(Timer.builder(name).tags(safeTags(tags)).register(registry))
        }
    }

    fun setGauge(name: String, value: Long, vararg tags: Pair<String, String>) {
        val registry = meterRegistry ?: return
        runCatching {
            val safeTags = safeTags(tags)
            val key = "$name|${safeTags.joinToString(",") { "${it.key}=${it.value}" }}"
            val gauge = gauges.computeIfAbsent(key) {
                AtomicLong(0).also {
                    registry.gauge(name, safeTags, it)
                }
            }
            gauge.set(value.coerceAtLeast(0L))
        }
    }

    fun confidenceBucket(confidence: BigDecimal?): String =
        when {
            confidence == null -> "none"
            confidence < BigDecimal("0.50") -> "low"
            confidence < BigDecimal("0.80") -> "medium"
            else -> "high"
        }

    fun endpointGroup(path: String?): String {
        val normalized = path.orEmpty()
        return when {
            normalized.startsWith("/api/v1/auth") -> "auth"
            normalized.startsWith("/api/v1/assistant") -> "assistant"
            normalized.startsWith("/api/v1/tasks") -> "tasks"
            normalized.startsWith("/api/v1/notifications") -> "notifications"
            normalized.startsWith("/api/v1/dashboard/reports") -> "reporting"
            normalized.startsWith("/api/v1/dashboard") -> "dashboard"
            normalized.startsWith("/api/v1/dev/pms") -> "dev_pms"
            normalized.startsWith("/api/v1/internal/pms") -> "internal_pms"
            normalized.startsWith("/actuator") -> "actuator"
            else -> "other"
        }
    }

    fun provider(value: String?): String {
        val normalized = tagValue(value)
        return when (normalized) {
            "deterministic_local", "deterministic-local", "deterministic" -> "deterministic"
            "openai" -> "openai"
            "none", "unknown" -> "unavailable"
            else -> "other"
        }
    }

    fun sourceType(hasAssistantMessage: Boolean, hasVisionAnalysis: Boolean): String =
        when {
            hasAssistantMessage && hasVisionAnalysis -> "mixed"
            hasVisionAnalysis -> "vision_analysis"
            hasAssistantMessage -> "assistant_message"
            else -> "none"
        }

    private fun safeTags(tags: Array<out Pair<String, String>>): Tags =
        Tags.of(
            *tags
                .filter { (key, _) -> key in ALLOWED_TAGS }
                .flatMap { (key, value) -> listOf(key, tagValue(value)) }
                .toTypedArray()
        )

    private fun tagValue(value: String?): String {
        val normalized = value.orEmpty()
            .trim()
            .lowercase()
            .map { char ->
                when {
                    char.isLetterOrDigit() -> char
                    char == '_' || char == '-' -> char
                    else -> '_'
                }
            }
            .joinToString("")
            .trim('_', '-')
            .take(64)

        return normalized.ifBlank { "unknown" }
    }

    companion object {
        private val ALLOWED_TAGS = setOf(
            "operation",
            "outcome",
            "provider",
            "confidence_bucket",
            "status",
            "source_type",
            "endpoint_group",
            "event_type",
            "failure_category",
            "job",
            "reason_code",
            "range",
            "transition",
            "trigger"
        )

        fun noop(): OperationalObservability = OperationalObservability(null)
    }
}
