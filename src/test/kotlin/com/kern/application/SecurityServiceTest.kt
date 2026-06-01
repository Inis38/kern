package com.kern.application

import com.kern.domain.port.SecurityAnalyzer
import com.kern.domain.security.AuthPeriodStats
import com.kern.domain.security.AuthStats
import com.kern.domain.security.SecurityOverview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SecurityServiceTest {

    @Test
    fun `overview delegates to analyzer`() {
        val expected = SecurityOverview(
            hostname = "srv",
            auth = AuthStats(
                available = true,
                logPath = "/var/log/auth.log",
                unavailableReason = null,
                last24Hours = AuthPeriodStats(1, 0, 1, 0),
                last7Days = AuthPeriodStats(2, 1, 1, 1),
                recentEvents = emptyList(),
            ),
            recommendations = emptyList(),
        )
        val service = SecurityService(
            securityAnalyzer = object : SecurityAnalyzer {
                override fun analyze() = expected
            },
        )

        assertEquals(expected, service.overview())
    }
}
