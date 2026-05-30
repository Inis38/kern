package com.kern.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kern.apps")
data class AppsProperties(
    val commandTimeoutSeconds: Long = 300,
    val nginx: NginxAppProperties = NginxAppProperties(),
)

data class NginxAppProperties(
    val siteConfigPath: String = "/etc/nginx/sites-available/kern-managed",
    val enabledLinkPath: String = "/etc/nginx/sites-enabled/kern-managed",
    val settingsPath: String = "/var/lib/kern/apps/nginx/settings.properties",
)
