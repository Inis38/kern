package com.kern.web

import com.kern.application.AppsService
import com.kern.application.MonitoringService
import com.kern.application.FileManagerService
import com.kern.application.SecurityService
import com.kern.domain.files.DirectoryListing
import com.kern.domain.files.FileEntry
import com.kern.domain.files.FileEntryType
import com.kern.domain.security.AuthPeriodStats
import com.kern.domain.security.AuthStats
import com.kern.domain.security.SecurityOverview
import com.kern.application.ManagedAppOverview
import com.kern.application.MonitoringSnapshot
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.ServiceState
import com.kern.domain.model.CpuMetrics
import com.kern.domain.model.HealthLevel
import com.kern.domain.model.HealthStatus
import com.kern.domain.model.HostInfo
import com.kern.domain.model.MemoryMetrics
import com.kern.domain.model.SystemMetrics
import com.kern.web.api.AppsApiController
import com.kern.web.api.MonitoringApiController
import com.kern.web.api.FileManagerApiController
import com.kern.web.api.SecurityApiController
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(
    controllers = [
        MonitoringApiController::class,
        AppsApiController::class,
        SecurityApiController::class,
        FileManagerApiController::class,
    ],
)
@Import(WebMvcTestConfig::class)
class ApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var monitoringService: MonitoringService

    @MockitoBean
    private lateinit var appsService: AppsService

    @MockitoBean
    private lateinit var securityService: SecurityService

    @MockitoBean
    private lateinit var fileManagerService: FileManagerService

    @Test
    fun `monitoring api returns json`() {
        val metrics = SystemMetrics(
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            host = HostInfo("host", "Linux", "amd64", 2),
            cpu = CpuMetrics(10.0),
            memory = MemoryMetrics(100, 50, 50, 50.0),
            disks = emptyList(),
            uptimeSeconds = 100,
            loadAverage = null,
        )
        `when`(monitoringService.snapshot()).thenReturn(
            MonitoringSnapshot(metrics, HealthStatus(HealthLevel.HEALTHY, "ok")),
        )

        mockMvc.perform(get("/api/v1/monitoring").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.host.hostname").value("host"))
            .andExpect(jsonPath("$.cpu.percent").value(10.0))
    }

    @Test
    fun `security api returns json`() {
        `when`(securityService.overview()).thenReturn(
            SecurityOverview(
                hostname = "secure-host",
                auth = AuthStats(
                    available = true,
                    logPath = "/var/log/auth.log",
                    unavailableReason = null,
                    last24Hours = AuthPeriodStats(2, 1, 1, 1),
                    last7Days = AuthPeriodStats(5, 2, 3, 2),
                    recentEvents = emptyList(),
                ),
                recommendations = emptyList(),
            ),
        )

        mockMvc.perform(get("/api/v1/security").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hostname").value("secure-host"))
            .andExpect(jsonPath("$.auth.last24Hours.sshTotal").value(2))
            .andExpect(jsonPath("$.statusLevel").value("healthy"))
    }

    @Test
    fun `files api returns directory listing`() {
        `when`(fileManagerService.list("/tmp")).thenReturn(
            DirectoryListing(
                path = "/tmp",
                parentPath = "/",
                entries = listOf(
                    FileEntry(
                        name = "note.txt",
                        path = "/tmp/note.txt",
                        type = FileEntryType.FILE,
                        sizeBytes = 4,
                        modifiedAt = Instant.parse("2026-01-01T00:00:00Z"),
                        readable = true,
                        writable = true,
                    ),
                ),
            ),
        )

        mockMvc.perform(get("/api/v1/files").param("path", "/tmp").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.path").value("/tmp"))
            .andExpect(jsonPath("$.entries[0].name").value("note.txt"))
            .andExpect(jsonPath("$.entries[0].type").value("file"))
    }

    @Test
    fun `apps api returns list`() {
        `when`(appsService.list()).thenReturn(
            listOf(
                ManagedAppOverview(
                    id = ManagedAppId.NGINX,
                    slug = "nginx",
                    displayName = "Nginx",
                    description = "Web server",
                    status = ManagedAppStatus(
                        appId = ManagedAppId.NGINX,
                        installState = InstallState.NOT_INSTALLED,
                        serviceState = ServiceState.UNKNOWN,
                        version = null,
                        detail = null,
                    ),
                ),
            ),
        )

        mockMvc.perform(get("/api/v1/apps").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].slug").value("nginx"))
    }
}
