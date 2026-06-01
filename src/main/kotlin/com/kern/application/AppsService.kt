package com.kern.application

import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.RunningContainer
import com.kern.domain.port.ManagedApplication
import com.kern.domain.port.RunningContainerProvider
import com.kern.infrastructure.apps.ManagedApplicationRegistry
import org.springframework.stereotype.Service

data class ManagedAppOverview(
    val id: ManagedAppId,
    val slug: String,
    val displayName: String,
    val description: String,
    val status: ManagedAppStatus,
)

@Service
class AppsService(
    private val registry: ManagedApplicationRegistry,
) {

    fun list(): List<ManagedAppOverview> =
        registry.all().map { app ->
            ManagedAppOverview(
                id = app.id,
                slug = app.id.slug,
                displayName = app.id.displayName,
                description = app.description,
                status = app.status(),
            )
        }

    fun get(slug: String): ManagedApplication? = registry.get(slug)

    fun install(slug: String): AppOperationResult {
        val app = requireApp(slug)
        return app.install()
    }

    fun loadConfig(slug: String): List<AppConfigField> {
        val app = requireApp(slug)
        return app.loadConfig()
    }

    fun saveConfig(slug: String, values: Map<String, String>): AppOperationResult {
        val app = requireApp(slug)
        return app.saveConfig(values)
    }

    fun status(slug: String): ManagedAppStatus = requireApp(slug).status()

    fun listRunningContainers(slug: String): List<RunningContainer> {
        val app = registry.get(slug) ?: return emptyList()
        return (app as? RunningContainerProvider)?.listRunningContainers() ?: emptyList()
    }

    private fun requireApp(slug: String): ManagedApplication =
        registry.get(slug)
            ?: throw NoSuchElementException("Приложение не найдено: $slug")
}
