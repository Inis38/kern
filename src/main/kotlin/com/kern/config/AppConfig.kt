package com.kern.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    MonitoringProperties::class,
    AppsProperties::class,
    SecurityProperties::class,
    FileManagerProperties::class,
)
class AppConfig
