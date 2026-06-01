package com.kern.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kern.security")
data class SecurityProperties(
    /** Таймаут для быстрых shell-проверок (ufw, fail2ban, hostname). */
    val commandTimeoutSeconds: Long = 8,
    /** Таймаут чтения журнала аутентификации (tail). */
    val authLogTimeoutSeconds: Long = 12,
    /** Сколько последних строк auth.log читать. */
    val authLogTailLines: Int = 3000,
)
