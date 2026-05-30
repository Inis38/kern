package com.kern.web.dto

import com.kern.application.MonitoringSnapshot
import com.kern.domain.model.DiskMetrics
import com.kern.domain.model.HealthStatus

data class MonitoringResponse(
    val timestamp: String,
    val health: HealthResponse,
    val host: HostResponse,
    val cpu: UsageResponse,
    val memory: MemoryResponse,
    val disks: List<DiskResponse>,
    val uptimeSeconds: Long,
    val uptimeFormatted: String,
    val loadAverage: LoadResponse?,
)

data class HealthResponse(
    val level: String,
    val message: String,
    val checks: List<HealthCheckResponse>,
)

data class HealthCheckResponse(
    val name: String,
    val level: String,
    val message: String,
)

data class HostResponse(
    val hostname: String,
    val os: String,
    val arch: String,
    val processorCount: Int,
)

data class UsageResponse(val percent: Double)

data class MemoryResponse(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val percent: Double,
    val totalFormatted: String,
    val usedFormatted: String,
)

data class DiskResponse(
    val name: String,
    val mount: String,
    val percent: Double,
    val totalFormatted: String,
    val usedFormatted: String,
)

data class LoadResponse(
    val one: Double,
    val five: Double,
    val fifteen: Double,
)

fun MonitoringSnapshot.toResponse(): MonitoringResponse {
    val m = metrics
    return MonitoringResponse(
        timestamp = m.timestamp.toString(),
        health = health.toResponse(),
        host = HostResponse(
            hostname = m.host.hostname,
            os = m.host.os,
            arch = m.host.arch,
            processorCount = m.host.processorCount,
        ),
        cpu = UsageResponse(m.cpu.usagePercent),
        memory = MemoryResponse(
            totalBytes = m.memory.totalBytes,
            usedBytes = m.memory.usedBytes,
            availableBytes = m.memory.availableBytes,
            percent = m.memory.usagePercent,
            totalFormatted = formatBytes(m.memory.totalBytes),
            usedFormatted = formatBytes(m.memory.usedBytes),
        ),
        disks = m.disks.map { it.toResponse() },
        uptimeSeconds = m.uptimeSeconds,
        uptimeFormatted = formatUptime(m.uptimeSeconds),
        loadAverage = m.loadAverage?.let {
            LoadResponse(it.oneMinute, it.fiveMinutes, it.fifteenMinutes)
        },
    )
}

private fun HealthStatus.toResponse() = HealthResponse(
    level = level.name.lowercase(),
    message = message,
    checks = checks.map {
        HealthCheckResponse(
            name = it.name,
            level = it.level.name.lowercase(),
            message = it.message,
        )
    },
)

private fun DiskMetrics.toResponse() = DiskResponse(
    name = name,
    mount = mount,
    percent = usagePercent,
    totalFormatted = formatBytes(totalBytes),
    usedFormatted = formatBytes(usedBytes),
)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    do {
        value /= 1024
        unitIndex++
    } while (value >= 1024 && unitIndex < units.lastIndex)
    return "%.1f %s".format(value, units[unitIndex])
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
