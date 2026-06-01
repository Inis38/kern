package com.kern.infrastructure.security

import com.kern.domain.security.RecommendationSeverity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SshdConfigInspectorTest {

    @Test
    fun `mergeConfig keeps last value and ignores comments`() {
        val target = linkedMapOf<String, String>()
        SshdConfigInspector.mergeConfig(
            target,
            """
            # comment
            PermitRootLogin no
            PasswordAuthentication yes
            """.trimIndent(),
        )
        SshdConfigInspector.mergeConfig(target, "PermitRootLogin yes")

        assertEquals("yes", target["PermitRootLogin"])
        assertEquals("yes", target["PasswordAuthentication"])
    }

    @Test
    fun `recommendations flag root login and password auth`() {
        val recs = SshdConfigInspector.recommendations(
            mapOf(
                "PermitRootLogin" to "yes",
                "PasswordAuthentication" to "yes",
            ),
        )

        assertEquals(2, recs.size)
        assertTrue(recs.any { it.id == "ssh-root-login" && it.severity == RecommendationSeverity.CRITICAL })
        assertTrue(recs.any { it.id == "ssh-password-auth" && it.severity == RecommendationSeverity.WARNING })
    }
}
