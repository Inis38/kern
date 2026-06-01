package com.kern.infrastructure.security

import com.kern.domain.security.AuthLoginEvent
import com.kern.domain.security.AuthMethod
import com.kern.domain.security.AuthPeriodStats
import com.kern.domain.security.AuthStats
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AuthLogParser {

    private val linePrefix = Regex("""^(\w{3})\s+(\d{1,2})\s+(\d{2}:\d{2}:\d{2})""")
    private val sshAccepted = Regex(
        """sshd\[\d+]: Accepted (publickey|password|keyboard-interactive(?:/password)?) for (\S+) from (\S+)""",
    )
    private val passwordAccepted = Regex(
        """Accepted password for (\S+) from (\S+)""",
    )
    private val pamPasswordSuccess = Regex(
        """pam_unix(?:\([^)]+\))?: authentication success(?:;.*)? user=(\S+)""",
    )

    fun parse(logContent: String, now: Instant, zoneId: ZoneId = ZoneId.systemDefault()): AuthStats {
        val events = logContent.lineSequence()
            .mapNotNull { parseLine(it, now, zoneId) }
            .sortedByDescending { it.timestamp }
            .toList()

        return AuthStats(
            available = true,
            logPath = null,
            unavailableReason = null,
            last24Hours = aggregate(events, now, Duration.ofHours(24)),
            last7Days = aggregate(events, now, Duration.ofDays(7)),
            recentEvents = events.take(15),
        )
    }

    fun unavailable(reason: String): AuthStats =
        AuthStats(
            available = false,
            logPath = null,
            unavailableReason = reason,
            last24Hours = emptyPeriod(),
            last7Days = emptyPeriod(),
            recentEvents = emptyList(),
        )

    internal fun parseLine(line: String, now: Instant, zoneId: ZoneId): AuthLoginEvent? {
        val timestamp = parseTimestamp(line, now, zoneId) ?: return null
        sshAccepted.find(line)?.let { match ->
            val methodToken = match.groupValues[1]
            val method = when {
                methodToken.startsWith("publickey") -> AuthMethod.SSH_KEY
                else -> AuthMethod.SSH_PASSWORD
            }
            return AuthLoginEvent(
                timestamp = timestamp,
                user = match.groupValues[2],
                source = match.groupValues[3],
                method = method,
            )
        }

        if ("sshd[" !in line) {
            passwordAccepted.find(line)?.let { match ->
                return AuthLoginEvent(
                    timestamp = timestamp,
                    user = match.groupValues[1],
                    source = match.groupValues[2],
                    method = AuthMethod.PASSWORD,
                )
            }
            pamPasswordSuccess.find(line)?.let { match ->
                return AuthLoginEvent(
                    timestamp = timestamp,
                    user = match.groupValues[1],
                    source = "local",
                    method = AuthMethod.PASSWORD,
                )
            }
        }

        return null
    }

    private fun aggregate(events: List<AuthLoginEvent>, now: Instant, window: Duration): AuthPeriodStats {
        val cutoff = now.minus(window)
        val inWindow = events.filter { !it.timestamp.isBefore(cutoff) }
        val ssh = inWindow.filter { it.method == AuthMethod.SSH_KEY || it.method == AuthMethod.SSH_PASSWORD }
        val password = inWindow.filter { it.method == AuthMethod.PASSWORD || it.method == AuthMethod.SSH_PASSWORD }
        return AuthPeriodStats(
            sshTotal = ssh.size,
            sshPassword = ssh.count { it.method == AuthMethod.SSH_PASSWORD },
            sshPublicKey = ssh.count { it.method == AuthMethod.SSH_KEY },
            passwordTotal = password.distinctBy { "${it.timestamp}|${it.user}|${it.source}" }.size,
        )
    }

    private fun emptyPeriod() = AuthPeriodStats(0, 0, 0, 0)

    private val monthParser = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    private fun parseTimestamp(line: String, now: Instant, zoneId: ZoneId): Instant? {
        val match = linePrefix.find(line) ?: return null
        val month = runCatching { Month.from(monthParser.parse(match.groupValues[1])) }.getOrNull() ?: return null
        val day = match.groupValues[2].trim().toIntOrNull() ?: return null
        val time = LocalDateTime.parse(
            "1970-${month.value.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}T${match.groupValues[3]}",
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )
        var year = now.atZone(zoneId).year
        var candidate = time.withYear(year).atZone(zoneId).toInstant()
        if (candidate.isAfter(now.plus(Duration.ofDays(1)))) {
            year -= 1
            candidate = time.withYear(year).atZone(zoneId).toInstant()
        }
        return candidate
    }
}
