package com.kern.domain.port

import com.kern.domain.model.SystemMetrics

/**
 * Port for collecting system metrics. New collectors (e.g. network, processes)
 * can be added as separate implementations and composed in the application layer.
 */
interface MetricsCollector {
    fun collect(): SystemMetrics
}
