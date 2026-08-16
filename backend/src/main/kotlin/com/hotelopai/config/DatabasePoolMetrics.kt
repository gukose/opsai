package com.hotelopai.config

import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DatabasePoolMetrics(
    dataSource: DataSource,
    meterRegistry: MeterRegistry
) {
    init {
        val hikari = dataSource as? HikariDataSource
        if (hikari != null) {
            fun gauge(name: String, value: () -> Double) {
                Gauge.builder("hotelopai.db.pool.$name", hikari) { value() }
                    .description("Hotel OpAI database connection pool $name")
                    .register(meterRegistry)
            }
            gauge("active") { hikari.hikariPoolMXBean?.activeConnections?.toDouble() ?: 0.0 }
            gauge("idle") { hikari.hikariPoolMXBean?.idleConnections?.toDouble() ?: 0.0 }
            gauge("pending") { hikari.hikariPoolMXBean?.threadsAwaitingConnection?.toDouble() ?: 0.0 }
            gauge("max") { hikari.maximumPoolSize.toDouble() }
        }
    }
}
