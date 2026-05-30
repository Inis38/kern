package com.kern.infrastructure.apps.ufw

import com.kern.domain.apps.ConfigFieldType

object UfwConfigTemplates {

    val definitions = listOf(
        "enabled" to UfwFieldDef(
            label = "Брандмауэр включён",
            defaultValue = "no",
            description = "Активен ли UFW (yes/no)",
            section = "Состояние",
            type = ConfigFieldType.TEXT,
        ),
        "defaultIncoming" to UfwFieldDef(
            label = "Входящие по умолчанию",
            defaultValue = "deny",
            description = "Политика для входящих соединений: deny, allow или reject",
            section = "Политики",
            type = ConfigFieldType.TEXT,
        ),
        "defaultOutgoing" to UfwFieldDef(
            label = "Исходящие по умолчанию",
            defaultValue = "allow",
            description = "Политика для исходящих соединений: deny, allow или reject",
            section = "Политики",
            type = ConfigFieldType.TEXT,
        ),
        "defaultForward" to UfwFieldDef(
            label = "Переадресация по умолчанию",
            defaultValue = "deny",
            description = "Политика для forwarded-трафика: deny, allow или reject",
            section = "Политики",
            type = ConfigFieldType.TEXT,
        ),
        "ipv6" to UfwFieldDef(
            label = "Поддержка IPv6",
            defaultValue = "yes",
            description = "Включить правила для IPv6 (yes/no)",
            section = "Параметры",
            type = ConfigFieldType.TEXT,
        ),
        "logLevel" to UfwFieldDef(
            label = "Уровень логирования",
            defaultValue = "low",
            description = "off, low, medium, high или full",
            section = "Параметры",
            type = ConfigFieldType.TEXT,
        ),
        "openPorts" to UfwFieldDef(
            label = "Открытые порты",
            defaultValue = "",
            description = "По одному порту на строку: 22, 80/tcp, 443/tcp, 53/udp",
            section = "Порты",
            type = ConfigFieldType.TEXTAREA,
            required = false,
        ),
    )

    val defaultValues: Map<String, String> =
        definitions.associate { (key, def) -> key to def.defaultValue }

    val readOnlyKeys: Set<String> = emptySet()

    fun normalizePolicy(value: String): String =
        value.trim().lowercase().let { normalized ->
            when (normalized) {
                "drop", "deny" -> "deny"
                "accept", "allow" -> "allow"
                "reject" -> "reject"
                else -> normalized
            }
        }

    fun policyToUfwFileValue(value: String): String = when (normalizePolicy(value)) {
        "allow" -> "ACCEPT"
        "reject" -> "REJECT"
        else -> "DROP"
    }

    fun policyFromUfwFileValue(value: String): String = when (value.trim().uppercase()) {
        "ACCEPT" -> "allow"
        "REJECT" -> "reject"
        else -> "deny"
    }
}

data class UfwFieldDef(
    val label: String,
    val defaultValue: String,
    val description: String,
    val section: String,
    val type: ConfigFieldType = ConfigFieldType.TEXT,
    val required: Boolean = true,
)
