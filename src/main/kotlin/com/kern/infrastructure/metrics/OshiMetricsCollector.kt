package com.kern.infrastructure.metrics

import com.kern.config.MonitoringProperties
import com.kern.domain.model.CpuMetrics
import com.kern.domain.model.DiskMetrics
import com.kern.domain.model.HostInfo
import com.kern.domain.model.LoadAverage
import com.kern.domain.model.MemoryMetrics
import com.kern.domain.model.SystemMetrics
import com.kern.domain.port.MetricsCollector
import org.springframework.stereotype.Component
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.hardware.GlobalMemory
import oshi.hardware.HardwareAbstractionLayer
import oshi.software.os.OperatingSystem
import java.time.Instant
import kotlin.math.round

@Component
class OshiMetricsCollector(
    private val properties: MonitoringProperties,
) : MetricsCollector {

    private val systemInfo = SystemInfo()
    private val hardware: HardwareAbstractionLayer = systemInfo.hardware
    private val os: OperatingSystem = systemInfo.operatingSystem
    private val processor: CentralProcessor = hardware.processor
    private val memory: GlobalMemory = hardware.memory

    override fun collect(): SystemMetrics {
        val timestamp = Instant.now()
        val cpuUsage = measureCpuUsage()
        val memoryMetrics = buildMemoryMetrics()
        val disks = buildDiskMetrics()
        val uptime = os.systemUptime
        val load = buildLoadAverage()

        return SystemMetrics(
            timestamp = timestamp,
            host = HostInfo(
                hostname = os.networkParams.hostName.ifBlank { "unknown" },
                os = "${os.family} ${os.versionInfo.version}",
                arch = System.getProperty("os.arch") ?: "unknown",
                processorCount = processor.logicalProcessorCount,
            ),
            cpu = CpuMetrics(usagePercent = cpuUsage),
            memory = memoryMetrics,
            disks = disks,
            uptimeSeconds = uptime,
            loadAverage = load,
        )
    }

    private fun measureCpuUsage(): Double {
        val delay = properties.cpuSampleDelayMs.coerceAtLeast(200)
        val ticks = processor.systemCpuLoadTicks
        Thread.sleep(delay)
        val load = processor.getSystemCpuLoadBetweenTicks(ticks)
        return roundPercent((load.coerceIn(0.0, 1.0)) * 100)
    }

    private fun buildMemoryMetrics(): MemoryMetrics {
        val total = memory.total
        val available = memory.available
        val used = total - available
        val usagePercent = if (total > 0) roundPercent(used.toDouble() / total * 100) else 0.0
        return MemoryMetrics(
            totalBytes = total,
            usedBytes = used,
            availableBytes = available,
            usagePercent = usagePercent,
        )
    }

    private fun buildDiskMetrics(): List<DiskMetrics> =
        os.fileSystem.fileStores
            .mapNotNull { store ->
                val total = store.totalSpace
                if (total <= 0) return@mapNotNull null
                val usable = store.usableSpace
                val used = total - usable
                DiskMetrics(
                    name = store.name,
                    mount = store.volume,
                    totalBytes = total,
                    usedBytes = used,
                    usagePercent = roundPercent(used.toDouble() / total * 100),
                )
            }
            .sortedByDescending { it.totalBytes }

    private fun buildLoadAverage(): LoadAverage? {
        val loads = processor.getSystemLoadAverage(3)
        if (loads.size < 3 || loads.all { it < 0 }) return null
        return LoadAverage(
            oneMinute = round(loads[0] * 100) / 100,
            fiveMinutes = round(loads[1] * 100) / 100,
            fifteenMinutes = round(loads[2] * 100) / 100,
        )
    }

    private fun roundPercent(value: Double): Double = round(value * 10) / 10
}
