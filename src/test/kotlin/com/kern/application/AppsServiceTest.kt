package com.kern.application

import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppConfigFieldDefinition
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.RunningContainer
import com.kern.domain.apps.ServiceState
import com.kern.domain.port.ManagedApplication
import com.kern.domain.port.RunningContainerProvider
import com.kern.infrastructure.apps.ManagedApplicationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppsServiceTest {

    private val nginx = object : ManagedApplication {
        override val id = ManagedAppId.NGINX
        override val description = "test"

        override fun status() = ManagedAppStatus(
            appId = id,
            installState = InstallState.NOT_INSTALLED,
            serviceState = ServiceState.UNKNOWN,
            version = null,
            detail = null,
        )

        override fun install() = AppOperationResult(true, "ok")
        override fun configDefinitions() = listOf(
            AppConfigFieldDefinition("listenPort", "Port"),
        )
        override fun loadConfig() = listOf(
            AppConfigField("listenPort", "Port", com.kern.domain.apps.ConfigFieldType.TEXT, "80"),
        )
        override fun saveConfig(values: Map<String, String>) = AppOperationResult(true, "saved")
    }

    private val docker = object : ManagedApplication, RunningContainerProvider {
        override val id = ManagedAppId.DOCKER
        override val description = "docker test"

        override fun status() = ManagedAppStatus(
            appId = id,
            installState = InstallState.INSTALLED,
            serviceState = ServiceState.RUNNING,
            version = "27.0",
            detail = null,
        )

        override fun install() = AppOperationResult(true, "ok")
        override fun configDefinitions(): List<AppConfigFieldDefinition> = emptyList()
        override fun loadConfig(): List<AppConfigField> = emptyList()
        override fun saveConfig(values: Map<String, String>) = AppOperationResult(true, "saved")

        override fun listRunningContainers() = listOf(
            RunningContainer("id1", "web", "nginx", "Up", "80:80"),
        )
    }

    private val service = AppsService(ManagedApplicationRegistry(listOf(nginx, docker)))

    @Test
    fun `list returns registered apps`() {
        val apps = service.list()
        assertEquals(2, apps.size)
        assertEquals("nginx", apps[0].slug)
        assertEquals("docker", apps[1].slug)
    }

    @Test
    fun `install delegates to application`() {
        val result = service.install("nginx")
        assertTrue(result.success)
    }

    @Test
    fun `listRunningContainers delegates to provider`() {
        val containers = service.listRunningContainers("docker")
        assertEquals(1, containers.size)
        assertEquals("web", containers[0].name)
    }

    @Test
    fun `listRunningContainers returns empty for apps without provider`() {
        assertTrue(service.listRunningContainers("nginx").isEmpty())
    }

    @Test
    fun `listRunningContainers returns empty for unknown slug`() {
        assertTrue(service.listRunningContainers("unknown").isEmpty())
    }
}
