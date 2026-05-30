package com.kern.domain.model

import java.time.Instant

data class SystemMetrics(
    val timestamp: Instant,
    val host: HostInfo,
    val cpu: CpuMetrics,
    val memory: MemoryMetrics,
    val disks: List<DiskMetrics>,
    val uptimeSeconds: Long,
    val loadAverage: LoadAverage?,
)

data class HostInfo(
    val hostname: String,
    val os: String,
    val arch: String,
    val processorCount: Int,
)

data class CpuMetrics(
    val usagePercent: Double,
)

data class MemoryMetrics(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val usagePercent: Double,
)

data class DiskMetrics(
    val name: String,
    val mount: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val usagePercent: Double,
)

data class LoadAverage(
    val oneMinute: Double,
    val fiveMinutes: Double,
    val fifteenMinutes: Double,
)
