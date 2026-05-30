package com.kern.application

import com.kern.domain.model.HealthStatus
import com.kern.domain.model.SystemMetrics
import com.kern.domain.port.HealthEvaluator
import com.kern.domain.port.MetricsCollector
import org.springframework.stereotype.Service

data class MonitoringSnapshot(
    val metrics: SystemMetrics,
    val health: HealthStatus,
)

/**
 * Application facade for monitoring. Web layer and future modules (alerts, history)
 * should depend on this service rather than collectors directly.
 */
@Service
class MonitoringService(
    private val metricsCollector: MetricsCollector,
    private val healthEvaluator: HealthEvaluator,
) {

    fun snapshot(): MonitoringSnapshot {
        val metrics = metricsCollector.collect()
        val health = healthEvaluator.evaluate(metrics)
        return MonitoringSnapshot(metrics = metrics, health = health)
    }
}
