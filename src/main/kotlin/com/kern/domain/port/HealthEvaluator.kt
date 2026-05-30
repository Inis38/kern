package com.kern.domain.port

import com.kern.domain.model.HealthStatus
import com.kern.domain.model.SystemMetrics

/**
 * Evaluates overall server health from current metrics.
 * Thresholds and rules can be swapped or extended without changing collectors.
 */
interface HealthEvaluator {
    fun evaluate(metrics: SystemMetrics): HealthStatus
}
