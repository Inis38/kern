package com.kern.domain.apps

enum class InstallState {
    INSTALLED,
    NOT_INSTALLED,
    UNKNOWN,
}

enum class ServiceState {
    RUNNING,
    STOPPED,
    UNKNOWN,
}

data class ManagedAppStatus(
    val appId: ManagedAppId,
    val installState: InstallState,
    val serviceState: ServiceState,
    val version: String?,
    val detail: String?,
)

enum class ConfigFieldType {
    TEXT,
    NUMBER,
    TEXTAREA,
}

data class AppConfigFieldDefinition(
    val key: String,
    val label: String,
    val type: ConfigFieldType = ConfigFieldType.TEXT,
    val description: String? = null,
    val required: Boolean = true,
    val section: String = "Основное",
)

data class AppConfigField(
    val key: String,
    val label: String,
    val type: ConfigFieldType,
    val value: String,
    val description: String? = null,
    val required: Boolean = true,
    val section: String = "Основное",
)

data class AppOperationResult(
    val success: Boolean,
    val message: String,
)
