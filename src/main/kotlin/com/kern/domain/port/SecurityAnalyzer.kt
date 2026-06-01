package com.kern.domain.port

import com.kern.domain.security.SecurityOverview

interface SecurityAnalyzer {
    fun analyze(): SecurityOverview
}
