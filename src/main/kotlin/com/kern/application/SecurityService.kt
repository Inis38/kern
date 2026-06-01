package com.kern.application

import com.kern.domain.port.SecurityAnalyzer
import com.kern.domain.security.SecurityOverview
import org.springframework.stereotype.Service

@Service
class SecurityService(
    private val securityAnalyzer: SecurityAnalyzer,
) {
    fun overview(): SecurityOverview = securityAnalyzer.analyze()
}
