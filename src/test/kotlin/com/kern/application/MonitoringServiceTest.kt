package com.kern.application

import com.kern.domain.model.CpuMetrics
import com.kern.domain.model.HealthLevel
import com.kern.domain.model.HealthStatus
import com.kern.domain.model.HostInfo
import com.kern.domain.model.MemoryMetrics
import com.kern.domain.model.SystemMetrics
import com.kern.domain.port.HealthEvaluator
import com.kern.domain.port.MetricsCollector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class MonitoringServiceTest {

    @Test
    fun `snapshot delegates to collector and evaluator`() {
        val metrics = SystemMetrics(
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            host = HostInfo("test", "Linux", "x86_64", 4),
            cpu = CpuMetrics(10.0),
            memory = MemoryMetrics(1000, 500, 500, 50.0),
            disks = emptyList(),
            uptimeSeconds = 3600,
            loadAverage = null,
        )
        val health = HealthStatus(HealthLevel.HEALTHY, "ok")
        val service = MonitoringService(
            metricsCollector = object : MetricsCollector {
                override fun collect() = metrics
            },
            healthEvaluator = object : HealthEvaluator {
                override fun evaluate(m: SystemMetrics) = health
            },
        )

        val snapshot = service.snapshot()
        assertEquals(metrics, snapshot.metrics)
        assertEquals(health, snapshot.health)
    }
}
