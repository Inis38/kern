package com.kern.web

import com.kern.application.AppsService
import com.kern.application.MonitoringService
import com.kern.application.MonitoringSnapshot
import com.kern.application.ManagedAppOverview
import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppConfigFieldDefinition
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.ConfigFieldType
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.RunningContainer
import com.kern.domain.apps.ServiceState
import com.kern.domain.model.CpuMetrics
import com.kern.domain.model.HealthLevel
import com.kern.domain.model.HealthStatus
import com.kern.domain.model.HostInfo
import com.kern.domain.model.MemoryMetrics
import com.kern.domain.model.SystemMetrics
import com.kern.domain.port.ManagedApplication
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(controllers = [DashboardController::class, AppsController::class, SecurityController::class])
@Import(WebMvcTestConfig::class)
class WebPagesIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var monitoringService: MonitoringService

    @MockitoBean
    private lateinit var appsService: AppsService

    @Test
    fun `dashboard renders successfully`() {
        stubMonitoring()
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Kern")))
            .andExpect(content().string(containsString("CPU")))
    }

    @Test
    fun `security page renders shell without blocking on data`() {
        mockMvc.perform(get("/security"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Безопасность")))
            .andExpect(content().string(containsString("Загрузка данных")))
            .andExpect(content().string(containsString("security.js")))
    }

    @Test
    fun `apps index renders successfully`() {
        `when`(appsService.list()).thenReturn(emptyList())
        mockMvc.perform(get("/apps"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Приложения")))
    }

    @Test
    fun `app detail renders successfully`() {
        stubNginxApp()
        mockMvc.perform(get("/apps/nginx"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Nginx")))
            .andExpect(content().string(containsString("Настройки")))
    }

    @Test
    fun `docker detail renders status and containers`() {
        stubDockerApp()
        `when`(appsService.listRunningContainers("docker")).thenReturn(
            listOf(
                RunningContainer(
                    id = "abc123",
                    name = "web",
                    image = "nginx:alpine",
                    status = "Up 1 hour",
                    ports = "0.0.0.0:80->80/tcp",
                ),
            ),
        )
        mockMvc.perform(get("/apps/docker"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Docker")))
            .andExpect(content().string(containsString("Запущенные контейнеры")))
            .andExpect(content().string(containsString("web")))
            .andExpect(content().string(containsString("nginx:alpine")))
    }

    @Test
    fun `ufw detail renders status and settings`() {
        stubUfwApp()
        mockMvc.perform(get("/apps/ufw"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("UFW")))
            .andExpect(content().string(containsString("Настройки")))
            .andExpect(content().string(containsString("Порты")))
    }

    @Test
    fun `app install redirects to detail`() {
        stubNginxApp()
        `when`(appsService.install("nginx")).thenReturn(AppOperationResult(true, "ok"))
        mockMvc.perform(post("/apps/nginx/install"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/apps/nginx"))
    }

    private fun stubMonitoring() {
        val metrics = SystemMetrics(
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            host = HostInfo("test-host", "Linux", "amd64", 4),
            cpu = CpuMetrics(12.5),
            memory = MemoryMetrics(1000, 500, 500, 50.0),
            disks = emptyList(),
            uptimeSeconds = 3600,
            loadAverage = null,
        )
        val health = HealthStatus(HealthLevel.HEALTHY, "All systems operational")
        `when`(monitoringService.snapshot()).thenReturn(MonitoringSnapshot(metrics, health))
    }

    private fun stubNginxApp() {
        val status = ManagedAppStatus(
            appId = ManagedAppId.NGINX,
            installState = InstallState.INSTALLED,
            serviceState = ServiceState.RUNNING,
            version = "1.24",
            detail = "Служба запущена",
        )
        val app = object : ManagedApplication {
            override val id = ManagedAppId.NGINX
            override val description = "Веб-сервер"

            override fun status() = status
            override fun install() = AppOperationResult(true, "ok")
            override fun configDefinitions() = listOf(
                AppConfigFieldDefinition("listenPort", "Порт", section = "Сервер"),
            )
            override fun loadConfig() = listOf(
                AppConfigField("listenPort", "Порт", ConfigFieldType.TEXT, "80", section = "Сервер"),
            )
            override fun saveConfig(values: Map<String, String>) = AppOperationResult(true, "saved")
        }
        `when`(appsService.get("nginx")).thenReturn(app)
    }

    private fun stubUfwApp() {
        val status = ManagedAppStatus(
            appId = ManagedAppId.UFW,
            installState = InstallState.INSTALLED,
            serviceState = ServiceState.RUNNING,
            version = "0.36.2",
            detail = "Активен, по умолчанию: входящие: deny, исходящие: allow (2 открытых порта): 22, 80",
        )
        val app = object : ManagedApplication {
            override val id = ManagedAppId.UFW
            override val description = "Сетевой экран"

            override fun status() = status
            override fun install() = AppOperationResult(true, "ok")
            override fun configDefinitions() = listOf(
                AppConfigFieldDefinition("enabled", "Брандмауэр включён", section = "Состояние"),
                AppConfigFieldDefinition("openPorts", "Открытые порты", section = "Порты", type = ConfigFieldType.TEXTAREA),
            )
            override fun loadConfig() = listOf(
                AppConfigField("enabled", "Брандмауэр включён", ConfigFieldType.TEXT, "yes", section = "Состояние"),
                AppConfigField(
                    "openPorts",
                    "Открытые порты",
                    ConfigFieldType.TEXTAREA,
                    "22\n80/tcp",
                    section = "Порты",
                ),
            )
            override fun saveConfig(values: Map<String, String>) = AppOperationResult(true, "saved")
        }
        `when`(appsService.get("ufw")).thenReturn(app)
    }

    private fun stubDockerApp() {
        val status = ManagedAppStatus(
            appId = ManagedAppId.DOCKER,
            installState = InstallState.INSTALLED,
            serviceState = ServiceState.RUNNING,
            version = "27.5.1",
            detail = "Демон запущен, 1 контейнер",
        )
        val app = object : ManagedApplication {
            override val id = ManagedAppId.DOCKER
            override val description = "Контейнеризация"

            override fun status() = status
            override fun install() = AppOperationResult(true, "ok")
            override fun configDefinitions(): List<AppConfigFieldDefinition> = emptyList()
            override fun loadConfig(): List<AppConfigField> = emptyList()
            override fun saveConfig(values: Map<String, String>) =
                AppOperationResult(false, "Нет настроек")
        }
        `when`(appsService.get("docker")).thenReturn(app)
    }
}
