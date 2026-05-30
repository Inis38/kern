package com.kern.web.dto

import com.kern.application.ManagedAppOverview
import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.ServiceState

data class AppOverviewResponse(
    val slug: String,
    val displayName: String,
    val description: String,
    val installState: String,
    val serviceState: String,
    val version: String?,
    val detail: String?,
)

data class AppDetailResponse(
    val slug: String,
    val displayName: String,
    val description: String,
    val status: AppOverviewResponse,
    val config: List<AppConfigFieldResponse>,
)

data class AppConfigFieldResponse(
    val key: String,
    val label: String,
    val value: String,
    val section: String,
    val description: String?,
)

data class OperationResponse(
    val success: Boolean,
    val message: String,
)

fun ManagedAppOverview.toResponse() = AppOverviewResponse(
    slug = slug,
    displayName = displayName,
    description = description,
    installState = status.installState.name.lowercase(),
    serviceState = status.serviceState.name.lowercase(),
    version = status.version,
    detail = status.detail,
)

fun ManagedAppStatus.toResponse(slug: String, displayName: String, description: String) =
    AppOverviewResponse(
        slug = slug,
        displayName = displayName,
        description = description,
        installState = installState.name.lowercase(),
        serviceState = serviceState.name.lowercase(),
        version = version,
        detail = detail,
    )

fun AppConfigField.toResponse() = AppConfigFieldResponse(
    key = key,
    label = label,
    value = value,
    section = section,
    description = description,
)

fun AppOperationResult.toResponse() = OperationResponse(success, message)

fun InstallState.isInstalled(): Boolean = this == InstallState.INSTALLED

fun ServiceState.labelRu(): String = when (this) {
    ServiceState.RUNNING -> "Запущен"
    ServiceState.STOPPED -> "Остановлен"
    ServiceState.UNKNOWN -> "Неизвестно"
}

fun InstallState.labelRu(): String = when (this) {
    InstallState.INSTALLED -> "Установлен"
    InstallState.NOT_INSTALLED -> "Не установлен"
    InstallState.UNKNOWN -> "Неизвестно"
}
