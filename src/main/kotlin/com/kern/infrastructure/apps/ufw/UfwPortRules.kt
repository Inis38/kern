package com.kern.infrastructure.apps.ufw

data class UfwPortRule(
    val port: String,
    val protocol: String = "tcp",
) {
    val spec: String = "$port/$protocol"

    fun ufwAllowArg(): String = spec
}

object UfwPortRules {

    private val portPattern = Regex("""^(\d+(?::\d+)?)(?:/(tcp|udp))?$""", RegexOption.IGNORE_CASE)

    private val statusRulePattern = Regex(
        """^\[\s*\d+\]\s+(\S+?)(?:\s+\(v6\))?\s+ALLOW\s+IN\s+Anywhere""",
        RegexOption.IGNORE_CASE,
    )

    fun parseFromStatusOutput(output: String): List<UfwPortRule> =
        output.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val rawSpec = statusRulePattern.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                parseSpec(rawSpec)
            }
            .distinctBy { it.spec }
            .sortedWith(compareBy({ it.port.toIntOrNull() ?: Int.MAX_VALUE }, { it.protocol }))
            .toList()

    fun parseFromUserInput(input: String): List<UfwPortRule> =
        input.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                parseSpec(line) ?: throw IllegalArgumentException("Недопустимый формат порта: $line")
            }
            .distinctBy { it.spec }
            .sortedWith(compareBy({ it.port.toIntOrNull() ?: Int.MAX_VALUE }, { it.protocol }))
            .toList()

    fun formatForUser(rules: List<UfwPortRule>): String =
        if (rules.isEmpty()) {
            ""
        } else {
            rules.joinToString("\n") { rule ->
                if (rule.protocol.equals("tcp", ignoreCase = true)) {
                    rule.port
                } else {
                    rule.spec
                }
            }
        }

    fun validateUserInput(input: String): String? =
        try {
            parseFromUserInput(input)
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }

    private fun parseSpec(raw: String): UfwPortRule? {
        val normalized = raw.trim().lowercase()
        val match = portPattern.matchEntire(normalized) ?: return null
        val port = match.groupValues[1]
        val protocol = match.groupValues[2].ifEmpty { "tcp" }.lowercase()
        if (protocol !in setOf("tcp", "udp")) {
            return null
        }
        if (!isValidPortSpec(port)) {
            return null
        }
        return UfwPortRule(port = port, protocol = protocol)
    }

    private fun isValidPortSpec(port: String): Boolean {
        if (port.contains(':')) {
            val parts = port.split(':')
            if (parts.size != 2) return false
            return parts.all { isValidPort(it) }
        }
        return isValidPort(port)
    }

    private fun isValidPort(port: String): Boolean {
        val value = port.toIntOrNull() ?: return false
        return value in 1..65535
    }
}
