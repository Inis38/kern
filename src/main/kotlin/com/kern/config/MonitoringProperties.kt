package com.kern.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kern.monitoring")
data class MonitoringProperties(
    val refreshIntervalMs: Long = 5000,
    val cpuSampleDelayMs: Long = 500,
)
