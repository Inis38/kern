package com.kern.domain.model

enum class HealthLevel {
    HEALTHY,
    WARNING,
    CRITICAL,
}

data class HealthStatus(
    val level: HealthLevel,
    val message: String,
    val checks: List<HealthCheck> = emptyList(),
)

data class HealthCheck(
    val name: String,
    val level: HealthLevel,
    val message: String,
)
