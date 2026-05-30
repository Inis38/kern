package com.kern.infrastructure.apps.ufw

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UfwPortRulesTest {

    @Test
    fun `parseFromStatusOutput extracts unique allow rules`() {
        val output = """
            Status: active

            To                         Action      From
            --                         ------      ----
            [ 1] 22/tcp                     ALLOW IN    Anywhere
            [ 2] 80/tcp                     ALLOW IN    Anywhere
            [ 3] 22/tcp (v6)                ALLOW IN    Anywhere (v6)
            [ 4] 53/udp                     ALLOW IN    Anywhere
            [ 5] Anywhere                   ALLOW OUT   Anywhere
        """.trimIndent()

        val rules = UfwPortRules.parseFromStatusOutput(output)

        assertEquals(3, rules.size)
        assertEquals(listOf("22/tcp", "53/udp", "80/tcp"), rules.map { it.spec })
    }

    @Test
    fun `parseFromUserInput accepts ports with optional protocol`() {
        val input = """
            22
            80/tcp
            443/tcp
            53/udp
        """.trimIndent()

        val rules = UfwPortRules.parseFromUserInput(input)

        assertEquals(4, rules.size)
        assertEquals("22/tcp", rules.first { it.port == "22" }.spec)
        assertEquals("53/udp", rules.first { it.port == "53" }.spec)
    }

    @Test
    fun `formatForUser renders editable list`() {
        val formatted = UfwPortRules.formatForUser(
            listOf(
                UfwPortRule("22", "tcp"),
                UfwPortRule("53", "udp"),
            ),
        )

        assertEquals("22\n53/udp", formatted)
    }

    @Test
    fun `validateUserInput rejects invalid port`() {
        val error = UfwPortRules.validateUserInput("99999\nabc")

        assertTrue(error!!.contains("99999") || error.contains("abc"))
    }

    @Test
    fun `validateUserInput accepts empty list`() {
        assertNull(UfwPortRules.validateUserInput(""))
    }

    @Test
    fun `parseFromUserInput rejects invalid format`() {
        assertThrows<IllegalArgumentException> {
            UfwPortRules.parseFromUserInput("foo/bar")
        }
    }
}
