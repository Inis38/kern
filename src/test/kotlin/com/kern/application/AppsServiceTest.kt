package com.kern.application

import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppConfigFieldDefinition
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.ServiceState
import com.kern.domain.port.ManagedApplication
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

    private val service = AppsService(ManagedApplicationRegistry(listOf(nginx)))

    @Test
    fun `list returns registered apps`() {
        val apps = service.list()
        assertEquals(1, apps.size)
        assertEquals("nginx", apps.first().slug)
    }

    @Test
    fun `install delegates to application`() {
        val result = service.install("nginx")
        assertTrue(result.success)
    }
}
