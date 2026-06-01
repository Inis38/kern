package com.kern.infrastructure.security

import com.kern.domain.security.RecommendationSeverity
import com.kern.domain.security.SecurityRecommendation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

object SshdConfigInspector {

    private val sshdConfig = Path.of("/etc/ssh/sshd_config")
    private val sshdConfigDir = Path.of("/etc/ssh/sshd_config.d")

    fun readEffectiveSettings(): Map<String, String> {
        val values = linkedMapOf<String, String>()
        if (sshdConfig.exists()) {
            mergeConfig(values, sshdConfig.readText())
        }
        if (sshdConfigDir.exists() && sshdConfigDir.isDirectory()) {
            Files.list(sshdConfigDir).use { paths ->
                paths.filter { it.toString().endsWith(".conf") }
                    .sorted()
                    .forEach { mergeConfig(values, it.readText()) }
            }
        }
        return values
    }

    fun recommendations(settings: Map<String, String>): List<SecurityRecommendation> {
        val recommendations = mutableListOf<SecurityRecommendation>()

        settings["PermitRootLogin"]?.let { value ->
            if (value.equals("yes", ignoreCase = true)) {
                recommendations += SecurityRecommendation(
                    id = "ssh-root-login",
                    title = "Вход root по SSH разрешён",
                    message = "Отключите прямой вход root: установите PermitRootLogin no в /etc/ssh/sshd_config.",
                    severity = RecommendationSeverity.CRITICAL,
                )
            }
        }

        settings["PasswordAuthentication"]?.let { value ->
            if (value.equals("yes", ignoreCase = true)) {
                recommendations += SecurityRecommendation(
                    id = "ssh-password-auth",
                    title = "SSH-аутентификация по паролю включена",
                    message = "Для доступа по SSH предпочтительны ключи: PasswordAuthentication no после настройки ключей.",
                    severity = RecommendationSeverity.WARNING,
                )
            }
        }

        settings["PermitEmptyPasswords"]?.let { value ->
            if (value.equals("yes", ignoreCase = true)) {
                recommendations += SecurityRecommendation(
                    id = "ssh-empty-passwords",
                    title = "Разрешены пустые пароли SSH",
                    message = "Установите PermitEmptyPasswords no в конфигурации SSH.",
                    severity = RecommendationSeverity.CRITICAL,
                )
            }
        }

        return recommendations
    }

    internal fun mergeConfig(target: MutableMap<String, String>, content: String) {
        content.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Include") }
            .forEach { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    target[parts[0]] = parts[1]
                }
            }
    }
}
