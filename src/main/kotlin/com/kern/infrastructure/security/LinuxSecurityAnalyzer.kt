package com.kern.infrastructure.security

import com.kern.config.SecurityProperties
import com.kern.domain.port.SecurityAnalyzer
import com.kern.domain.security.AuthStats
import com.kern.domain.security.RecommendationSeverity
import com.kern.domain.security.SecurityOverview
import com.kern.domain.security.SecurityRecommendation
import com.kern.infrastructure.process.CommandExecutor
import com.kern.infrastructure.process.ProcessResult
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isReadable

@Component
class LinuxSecurityAnalyzer(
    private val commandExecutor: CommandExecutor,
    private val securityProperties: SecurityProperties,
    private val authLogCandidates: List<Path> = DEFAULT_AUTH_LOG_CANDIDATES,
) : SecurityAnalyzer {

    companion object {
        private val DEFAULT_AUTH_LOG_CANDIDATES = listOf(
            Path.of("/var/log/auth.log"),
            Path.of("/var/log/secure"),
        )
    }

    override fun analyze(): SecurityOverview {
        val hostname = runCommand("hostname -s 2>/dev/null || hostname")
            .output
            .ifBlank { "unknown" }

        return SecurityOverview(
            hostname = hostname,
            auth = loadAuthStats(),
            recommendations = buildRecommendations(),
        )
    }

    private fun loadAuthStats(): AuthStats {
        val logPath = authLogCandidates.firstOrNull { it.exists() && it.isReadable() }
        if (logPath == null) {
            return AuthLogParser.unavailable("Журнал auth.log / secure недоступен для чтения")
        }

        val tail = runCommand(
            "tail -n ${securityProperties.authLogTailLines} ${shellQuote(logPath)} 2>/dev/null",
            securityProperties.authLogTimeoutSeconds,
        )
        if (tail.timedOut) {
            return AuthLogParser.unavailable(
                "Чтение журнала прервано по таймауту (${securityProperties.authLogTimeoutSeconds} с)",
            )
        }
        if (!tail.success || tail.output.isBlank()) {
            return AuthLogParser.unavailable("Не удалось прочитать ${logPath.fileName}")
        }

        return AuthLogParser.parse(tail.output, Instant.now()).copy(logPath = logPath.toString())
    }

    private fun buildRecommendations(): List<SecurityRecommendation> {
        val items = mutableListOf<SecurityRecommendation>()
        items += SshdConfigInspector.recommendations(SshdConfigInspector.readEffectiveSettings())
        items += firewallRecommendations()
        items += fail2banRecommendation()
        return items.sortedByDescending { it.severity.ordinal }
    }

    private fun firewallRecommendations(): List<SecurityRecommendation> {
        val ufwCheck = runCommand("command -v ufw")
        if (ufwCheck.timedOut || !ufwCheck.success) {
            return listOf(
                SecurityRecommendation(
                    id = "firewall-missing",
                    title = "UFW не установлен",
                    message = "Рекомендуется включить сетевой экран (UFW) и открыть только необходимые порты.",
                    severity = RecommendationSeverity.WARNING,
                ),
            )
        }

        val status = runCommand("ufw status 2>&1")
        if (status.timedOut) {
            return emptyList()
        }
        if (status.output.contains("Status: inactive", ignoreCase = true)) {
            return listOf(
                SecurityRecommendation(
                    id = "ufw-inactive",
                    title = "Брандмауэр UFW выключен",
                    message = "Включите UFW: ufw enable, затем разрешите только нужные порты (например 22, 80, 443).",
                    severity = RecommendationSeverity.WARNING,
                ),
            )
        }
        return emptyList()
    }

    private fun fail2banRecommendation(): List<SecurityRecommendation> {
        val check = runCommand("command -v fail2ban-client")
        if (check.timedOut) {
            return emptyList()
        }
        if (check.success) {
            return emptyList()
        }
        return listOf(
            SecurityRecommendation(
                id = "fail2ban-missing",
                title = "Fail2ban не установлен",
                message = "Установите fail2ban для автоматической блокировки IP после неудачных попыток входа.",
                severity = RecommendationSeverity.INFO,
            ),
        )
    }

    private fun runCommand(command: String, timeoutSeconds: Long = securityProperties.commandTimeoutSeconds): ProcessResult =
        commandExecutor.runShell(command, timeoutSeconds)

    private fun shellQuote(path: Path): String =
        "'" + path.toString().replace("'", "'\\''") + "'"
}
