package com.kern.infrastructure.health

import com.kern.domain.model.HealthCheck
import com.kern.domain.model.HealthLevel
import com.kern.domain.model.HealthStatus
import com.kern.domain.model.SystemMetrics
import com.kern.domain.port.HealthEvaluator
import org.springframework.stereotype.Component

@Component
class DefaultHealthEvaluator : HealthEvaluator {

    override fun evaluate(metrics: SystemMetrics): HealthStatus {
        val checks = mutableListOf<HealthCheck>()

        val cpuLevel = levelForUsage(metrics.cpu.usagePercent, CPU_WARNING, CPU_CRITICAL)
        checks += HealthCheck("CPU", cpuLevel, "${metrics.cpu.usagePercent}%")

        val memLevel = levelForUsage(metrics.memory.usagePercent, MEM_WARNING, MEM_CRITICAL)
        checks += HealthCheck("Memory", memLevel, "${metrics.memory.usagePercent}%")

        val diskLevel = metrics.disks
            .maxOfOrNull { it.usagePercent }
            ?.let { levelForUsage(it, DISK_WARNING, DISK_CRITICAL) }
            ?: HealthLevel.HEALTHY
        val maxDisk = metrics.disks.maxOfOrNull { it.usagePercent } ?: 0.0
        checks += HealthCheck("Disk", diskLevel, "${maxDisk}%")

        val overall = checks.maxOf { it.level }
        val message = when (overall) {
            HealthLevel.HEALTHY -> "All systems operational"
            HealthLevel.WARNING -> "Some resources need attention"
            HealthLevel.CRITICAL -> "Critical resource pressure detected"
        }

        return HealthStatus(level = overall, message = message, checks = checks)
    }

    private fun levelForUsage(percent: Double, warning: Double, critical: Double): HealthLevel =
        when {
            percent >= critical -> HealthLevel.CRITICAL
            percent >= warning -> HealthLevel.WARNING
            else -> HealthLevel.HEALTHY
        }

    companion object {
        private const val CPU_WARNING = 75.0
        private const val CPU_CRITICAL = 90.0
        private const val MEM_WARNING = 80.0
        private const val MEM_CRITICAL = 92.0
        private const val DISK_WARNING = 85.0
        private const val DISK_CRITICAL = 95.0
    }
}
