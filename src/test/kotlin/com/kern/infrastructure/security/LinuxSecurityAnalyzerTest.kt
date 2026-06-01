package com.kern.infrastructure.security

import com.kern.config.SecurityProperties
import com.kern.domain.security.RecommendationSeverity
import com.kern.infrastructure.process.CommandExecutor
import com.kern.infrastructure.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LinuxSecurityAnalyzerTest {

    private val securityProperties = SecurityProperties(
        commandTimeoutSeconds = 5,
        authLogTimeoutSeconds = 10,
        authLogTailLines = 1000,
    )

    @Test
    fun `analyze returns hostname`() {
        val analyzer = analyzer(stubExecutor())
        assertEquals("test-host", analyzer.analyze().hostname)
    }

    @Test
    fun `firewall recommendation when ufw inactive`() {
        val analyzer = analyzer(
            stubExecutor { command, _ ->
                when {
                    command.startsWith("hostname") -> ProcessResult(0, "srv")
                    command.contains("tail") -> ProcessResult(1, "")
                    command.contains("command -v ufw") -> ProcessResult(0, "/usr/sbin/ufw")
                    command.contains("ufw status") -> ProcessResult(0, "Status: inactive")
                    command.contains("fail2ban") -> ProcessResult(1, "")
                    else -> ProcessResult(0, "")
                }
            },
        )

        val rec = analyzer.analyze().recommendations.first { it.id == "ufw-inactive" }
        assertEquals(RecommendationSeverity.WARNING, rec.severity)
    }

    @Test
    fun `auth unavailable when log cannot be read`() {
        val analyzer = analyzer(
            stubExecutor { command, _ ->
                when {
                    command.startsWith("hostname") -> ProcessResult(0, "srv")
                    command.contains("tail") -> ProcessResult(1, "")
                    command.contains("command -v ufw") -> ProcessResult(1, "")
                    command.contains("fail2ban") -> ProcessResult(1, "")
                    else -> ProcessResult(0, "")
                }
            },
        )

        assertFalse(analyzer.analyze().auth.available)
    }

    @Test
    fun `auth unavailable on tail timeout`(@TempDir dir: Path) {
        val logFile = dir.resolve("auth.log")
        Files.writeString(logFile, "Jun  1 10:00:00 host sshd[1]: Accepted publickey for a from 1.2.3.4 port 22\n")

        val analyzer = analyzer(
            stubExecutor { command, _ ->
                when {
                    command.startsWith("hostname") -> ProcessResult(0, "srv")
                    command.contains("tail") -> ProcessResult(-1, "", timedOut = true)
                    else -> ProcessResult(0, "")
                }
            },
            logs = listOf(logFile),
        )

        val auth = analyzer.analyze().auth
        assertFalse(auth.available)
        assertTrue(auth.unavailableReason!!.contains("таймауту"))
    }

    private fun stubExecutor(
        handler: (command: String, timeoutSeconds: Long?) -> ProcessResult = { command, _ ->
            when {
                command.startsWith("hostname") -> ProcessResult(0, "test-host")
                command.contains("tail") -> ProcessResult(1, "")
                command.contains("command -v ufw") -> ProcessResult(1, "")
                command.contains("fail2ban") -> ProcessResult(1, "")
                else -> ProcessResult(0, "")
            }
        },
    ): CommandExecutor = object : CommandExecutor {
        override fun runShell(command: String): ProcessResult = handler(command, null)

        override fun runShell(command: String, timeoutSeconds: Long): ProcessResult =
            handler(command, timeoutSeconds)
    }

    private fun analyzer(
        executor: CommandExecutor,
        logs: List<Path> = listOf(Path.of("/nonexistent/auth.log")),
    ): LinuxSecurityAnalyzer = LinuxSecurityAnalyzer(executor, securityProperties, logs)
}
