package com.kern.web

import com.kern.config.AppsProperties
import com.kern.config.MonitoringProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MonitoringProperties::class, AppsProperties::class)
class WebMvcTestConfig
