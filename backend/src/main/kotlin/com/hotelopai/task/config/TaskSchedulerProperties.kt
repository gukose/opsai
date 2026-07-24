package com.hotelopai.task.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ops.ai.task.overdue")
data class TaskSchedulerProperties(
    val enabled: Boolean = true,
    val lockTimeout: Duration = Duration.ofMinutes(10)
) {
    fun normalizedLockTimeout(): Duration =
        lockTimeout.takeIf { !it.isNegative && !it.isZero } ?: Duration.ofMinutes(10)
}
