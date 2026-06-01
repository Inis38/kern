package com.kern.domain.security

import java.time.Instant

enum class RecommendationSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

data class SecurityRecommendation(
    val id: String,
    val title: String,
    val message: String,
    val severity: RecommendationSeverity,
)

data class AuthLoginEvent(
    val timestamp: Instant,
    val user: String,
    val source: String,
    val method: AuthMethod,
)

enum class AuthMethod {
    SSH_KEY,
    SSH_PASSWORD,
    PASSWORD,
}

data class AuthPeriodStats(
    val sshTotal: Int,
    val sshPassword: Int,
    val sshPublicKey: Int,
    val passwordTotal: Int,
)

data class AuthStats(
    val available: Boolean,
    val logPath: String?,
    val unavailableReason: String?,
    val last24Hours: AuthPeriodStats,
    val last7Days: AuthPeriodStats,
    val recentEvents: List<AuthLoginEvent>,
)

data class SecurityOverview(
    val hostname: String,
    val auth: AuthStats,
    val recommendations: List<SecurityRecommendation>,
) {
    val worstSeverity: RecommendationSeverity? =
        recommendations.maxByOrNull { it.severity.ordinal }?.severity

    val statusLevel: String = when (worstSeverity) {
        null -> "healthy"
        RecommendationSeverity.INFO -> "healthy"
        RecommendationSeverity.WARNING -> "warning"
        RecommendationSeverity.CRITICAL -> "critical"
    }

    val statusMessage: String = when (worstSeverity) {
        null -> "Критичных проблем не обнаружено"
        RecommendationSeverity.INFO -> "Есть советы по усилению защиты"
        RecommendationSeverity.WARNING -> "Обнаружены риски безопасности"
        RecommendationSeverity.CRITICAL -> "Требуется срочное внимание"
    }
}
