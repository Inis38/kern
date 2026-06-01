package com.kern.infrastructure.security

import com.kern.domain.security.AuthMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class AuthLogParserTest {

    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val zone = ZoneId.of("UTC")

    @Test
    fun `parse counts ssh and password logins in windows`() {
        val log = """
            Jun  1 10:00:00 host sshd[1]: Accepted publickey for alice from 1.2.3.4 port 22 ssh2
            Jun  1 11:00:00 host sshd[2]: Accepted password for bob from 5.6.7.8 port 22 ssh2
            May 26 09:00:00 host sshd[3]: Accepted publickey for carol from 9.9.9.9 port 22 ssh2
            Jun  1 11:30:00 host su[4]: pam_unix(su:session): session opened for user root
        """.trimIndent()

        val stats = AuthLogParser.parse(log, now, zone)

        assertTrue(stats.available)
        assertEquals(2, stats.last24Hours.sshTotal)
        assertEquals(1, stats.last24Hours.sshPublicKey)
        assertEquals(1, stats.last24Hours.sshPassword)
        assertEquals(1, stats.last24Hours.passwordTotal)
        assertEquals(3, stats.last7Days.sshTotal)
        assertEquals(3, stats.recentEvents.size)
    }

    @Test
    fun `parseLine detects ssh key login`() {
        val line = "Jun  1 10:00:00 host sshd[1]: Accepted publickey for alice from 1.2.3.4 port 22 ssh2"
        val event = AuthLogParser.parseLine(line, now, zone)

        assertEquals(AuthMethod.SSH_KEY, event?.method)
        assertEquals("alice", event?.user)
        assertEquals("1.2.3.4", event?.source)
    }

    @Test
    fun `parseLine detects pam password success`() {
        val line = "Jun  1 10:05:00 host sudo: pam_unix(sudo:auth): authentication success; user=dave"
        val event = AuthLogParser.parseLine(line, now, zone)

        assertEquals(AuthMethod.PASSWORD, event?.method)
        assertEquals("dave", event?.user)
    }

    @Test
    fun `unavailable stats are empty`() {
        val stats = AuthLogParser.unavailable("нет доступа")
        assertFalse(stats.available)
        assertEquals("нет доступа", stats.unavailableReason)
        assertEquals(0, stats.last24Hours.sshTotal)
    }
}
