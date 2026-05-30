package com.kern.domain.port

import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppConfigFieldDefinition
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus

/**
 * Port for a managed server application (nginx, postgres, …).
 * Each implementation is registered as a Spring bean and discovered by the registry.
 */
interface ManagedApplication {
    val id: ManagedAppId
    val description: String

    fun status(): ManagedAppStatus
    fun install(): AppOperationResult
    fun configDefinitions(): List<AppConfigFieldDefinition>
    fun loadConfig(): List<AppConfigField>
    fun saveConfig(values: Map<String, String>): AppOperationResult
}
