package com.kern.web.dto

import com.kern.domain.security.AuthLoginEvent
import com.kern.domain.security.AuthMethod
import com.kern.domain.security.AuthPeriodStats
import com.kern.domain.security.AuthStats
import com.kern.domain.security.SecurityOverview
import com.kern.domain.security.SecurityRecommendation

data class SecurityResponse(
    val hostname: String,
    val statusLevel: String,
    val statusMessage: String,
    val auth: AuthStatsResponse,
    val recommendations: List<SecurityRecommendationResponse>,
)

data class AuthStatsResponse(
    val available: Boolean,
    val logPath: String?,
    val unavailableReason: String?,
    val last24Hours: AuthPeriodStatsResponse,
    val last7Days: AuthPeriodStatsResponse,
    val recentEvents: List<AuthLoginEventResponse>,
)

data class AuthPeriodStatsResponse(
    val sshTotal: Int,
    val sshPassword: Int,
    val sshPublicKey: Int,
    val passwordTotal: Int,
)

data class AuthLoginEventResponse(
    val timestamp: String,
    val user: String,
    val source: String,
    val method: String,
    val methodLabel: String,
)

data class SecurityRecommendationResponse(
    val id: String,
    val title: String,
    val message: String,
    val severity: String,
    val severityLabel: String,
)

fun SecurityOverview.toResponse(): SecurityResponse =
    SecurityResponse(
        hostname = hostname,
        statusLevel = statusLevel,
        statusMessage = statusMessage,
        auth = auth.toResponse(),
        recommendations = recommendations.map { it.toResponse() },
    )

private fun AuthStats.toResponse() = AuthStatsResponse(
    available = available,
    logPath = logPath,
    unavailableReason = unavailableReason,
    last24Hours = last24Hours.toResponse(),
    last7Days = last7Days.toResponse(),
    recentEvents = recentEvents.map { it.toResponse() },
)

private fun AuthPeriodStats.toResponse() = AuthPeriodStatsResponse(
    sshTotal = sshTotal,
    sshPassword = sshPassword,
    sshPublicKey = sshPublicKey,
    passwordTotal = passwordTotal,
)

private fun AuthLoginEvent.toResponse() = AuthLoginEventResponse(
    timestamp = timestamp.toString(),
    user = user,
    source = source,
    method = method.name,
    methodLabel = method.toLabel(),
)

private fun SecurityRecommendation.toResponse() = SecurityRecommendationResponse(
    id = id,
    title = title,
    message = message,
    severity = severity.name.lowercase(),
    severityLabel = severity.toLabel(),
)

private fun AuthMethod.toLabel(): String = when (this) {
    AuthMethod.SSH_KEY -> "SSH ключ"
    AuthMethod.SSH_PASSWORD -> "SSH пароль"
    AuthMethod.PASSWORD -> "Пароль"
}

private fun com.kern.domain.security.RecommendationSeverity.toLabel(): String = when (this) {
    com.kern.domain.security.RecommendationSeverity.CRITICAL -> "Критично"
    com.kern.domain.security.RecommendationSeverity.WARNING -> "Внимание"
    com.kern.domain.security.RecommendationSeverity.INFO -> "Совет"
}
