package com.kern.infrastructure.apps.ufw

import com.kern.config.AppsProperties
import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppConfigFieldDefinition
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.ServiceState
import com.kern.domain.port.ManagedApplication
import com.kern.infrastructure.process.CommandExecutor
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Component
class UfwManagedApplication(
    private val commandExecutor: CommandExecutor,
    private val appsProperties: AppsProperties,
) : ManagedApplication {

    override val id = ManagedAppId.UFW
    override val description = "Uncomplicated Firewall — управление сетевым экраном"

    private val ufwProps get() = appsProperties.ufw

    override fun status(): ManagedAppStatus {
        if (!isInstalled()) {
            return ManagedAppStatus(
                appId = id,
                installState = InstallState.NOT_INSTALLED,
                serviceState = ServiceState.UNKNOWN,
                version = null,
                detail = "UFW не найден в системе",
            )
        }

        val version = extractVersion(commandExecutor.runShell("ufw version 2>&1").output)
        val serviceState = detectServiceState()
        val verbose = commandExecutor.runShell("ufw status verbose 2>&1").output
        return ManagedAppStatus(
            appId = id,
            installState = InstallState.INSTALLED,
            serviceState = serviceState,
            version = version,
            detail = buildStatusDetail(serviceState, verbose),
        )
    }

    override fun install(): AppOperationResult {
        if (status().installState == InstallState.INSTALLED) {
            return AppOperationResult(true, "UFW уже установлен")
        }

        if (!commandExecutor.runShell("command -v apt-get").success) {
            return AppOperationResult(
                false,
                "Установка поддерживается только на Debian/Ubuntu (apt-get)",
            )
        }

        val install = commandExecutor.runShell(
            "export DEBIAN_FRONTEND=noninteractive && " +
                "apt-get update -qq && apt-get install -y -qq ufw",
        )
        if (!install.success) {
            return AppOperationResult(false, "Ошибка установки: ${install.output.take(500)}")
        }

        return AppOperationResult(true, "UFW успешно установлен")
    }

    override fun configDefinitions(): List<AppConfigFieldDefinition> =
        UfwConfigTemplates.definitions.map { (key, def) ->
            AppConfigFieldDefinition(
                key = key,
                label = def.label,
                type = def.type,
                description = def.description,
                required = def.required,
                section = def.section,
            )
        }

    override fun loadConfig(): List<AppConfigField> {
        val values = readCurrentSettings()
        return configDefinitions().map { def ->
            AppConfigField(
                key = def.key,
                label = def.label,
                type = def.type,
                value = values[def.key] ?: UfwConfigTemplates.defaultValues[def.key].orEmpty(),
                description = def.description,
                required = def.required,
                section = def.section,
            )
        }
    }

    override fun saveConfig(values: Map<String, String>): AppOperationResult {
        if (status().installState != InstallState.INSTALLED) {
            return AppOperationResult(false, "Сначала установите UFW")
        }

        val merged = UfwConfigTemplates.defaultValues.toMutableMap()
        merged.putAll(values.filterKeys { it !in UfwConfigTemplates.readOnlyKeys })

        val validationError = validateSettings(merged)
        if (validationError != null) {
            return AppOperationResult(false, validationError)
        }

        val portsResult = syncOpenPorts(merged["openPorts"].orEmpty())
        if (!portsResult.success) {
            return portsResult
        }

        writeDefaultConfig(merged)
        writeUfwConfig(merged)

        val enableResult = applyEnabledState(merged["enabled"].orEmpty())
        if (!enableResult.success) {
            return AppOperationResult(false, enableResult.message)
        }

        val reload = commandExecutor.runShell("ufw reload 2>&1")
        return if (reload.success) {
            AppOperationResult(true, "Настройки сохранены и применены")
        } else {
            AppOperationResult(false, "Сохранено, но перезагрузка не удалась: ${reload.output}")
        }
    }

    private fun isInstalled(): Boolean =
        commandExecutor.runShell("command -v ufw").success

    private fun detectServiceState(): ServiceState {
        val statusOutput = commandExecutor.runShell("ufw status 2>&1").output
        return when {
            statusOutput.contains("Status: active", ignoreCase = true) -> ServiceState.RUNNING
            statusOutput.contains("Status: inactive", ignoreCase = true) -> ServiceState.STOPPED
            else -> ServiceState.UNKNOWN
        }
    }

    private fun extractVersion(output: String): String? =
        Regex("ufw\\s+(\\S+)", RegexOption.IGNORE_CASE).find(output)?.groupValues?.get(1)

    private fun buildStatusDetail(serviceState: ServiceState, verboseOutput: String): String {
        val stateLabel = when (serviceState) {
            ServiceState.RUNNING -> "Активен"
            ServiceState.STOPPED -> "Неактивен"
            ServiceState.UNKNOWN -> "Состояние неизвестно"
        }

        val defaults = Regex(
            "Default:\\s*(.+)",
            RegexOption.IGNORE_CASE,
        ).find(verboseOutput)?.groupValues?.get(1)?.trim()

        val numbered = commandExecutor.runShell("ufw status numbered 2>&1").output
        val openPorts = UfwPortRules.parseFromStatusOutput(numbered)
        val rulesCount = openPorts.size
        val rulesPart = when (rulesCount) {
            0 -> "открытых портов нет"
            1 -> "1 открытый порт"
            in 2..4 -> "$rulesCount открытых порта"
            else -> "$rulesCount открытых портов"
        }

        return buildString {
            append(stateLabel)
            if (defaults != null) {
                append(", по умолчанию: ")
                append(formatDefaults(defaults))
            }
            append(" (")
            append(rulesPart)
            append(')')
            if (openPorts.isNotEmpty()) {
                append(": ")
                append(openPorts.joinToString(", ") { it.port })
            }
        }
    }

    private fun formatDefaults(raw: String): String =
        raw.replace("deny (incoming)", "входящие: deny", ignoreCase = true)
            .replace("allow (outgoing)", "исходящие: allow", ignoreCase = true)
            .replace("reject (incoming)", "входящие: reject", ignoreCase = true)
            .replace("reject (outgoing)", "исходящие: reject", ignoreCase = true)
            .replace("disabled (routed)", "forward: disabled", ignoreCase = true)

    private fun readCurrentSettings(): Map<String, String> {
        val defaults = readDefaultConfigValues()
        val ufwConf = readUfwConfigValues()
        val serviceState = detectServiceState()

        return mapOf(
            "enabled" to if (serviceState == ServiceState.RUNNING) "yes" else "no",
            "defaultIncoming" to defaults.inputPolicy,
            "defaultOutgoing" to defaults.outputPolicy,
            "defaultForward" to defaults.forwardPolicy,
            "ipv6" to defaults.ipv6,
            "logLevel" to ufwConf.logLevel,
            "openPorts" to fetchOpenPortsListing(),
        )
    }

    private fun fetchOpenPortsListing(): String {
        val numbered = commandExecutor.runShell("ufw status numbered 2>&1").output
        return UfwPortRules.formatForUser(UfwPortRules.parseFromStatusOutput(numbered))
    }

    private fun syncOpenPorts(input: String): AppOperationResult {
        val portsError = UfwPortRules.validateUserInput(input)
        if (portsError != null) {
            return AppOperationResult(false, portsError)
        }

        val desired = UfwPortRules.parseFromUserInput(input)
        val numbered = commandExecutor.runShell("ufw status numbered 2>&1").output
        val current = UfwPortRules.parseFromStatusOutput(numbered)

        val currentSpecs = current.map { it.spec }.toSet()
        val desiredSpecs = desired.map { it.spec }.toSet()

        for (rule in current.filter { it.spec !in desiredSpecs }) {
            val delete = commandExecutor.runShell("ufw --force delete allow ${rule.ufwAllowArg()} 2>&1")
            if (!delete.success) {
                return AppOperationResult(
                    false,
                    "Не удалось удалить правило ${rule.spec}: ${delete.output}",
                )
            }
        }

        for (rule in desired.filter { it.spec !in currentSpecs }) {
            val allow = commandExecutor.runShell("ufw allow ${rule.ufwAllowArg()} 2>&1")
            if (!allow.success) {
                return AppOperationResult(
                    false,
                    "Не удалось открыть порт ${rule.spec}: ${allow.output}",
                )
            }
        }

        return AppOperationResult(true, "Порты синхронизированы")
    }

    private data class DefaultConfigValues(
        val inputPolicy: String,
        val outputPolicy: String,
        val forwardPolicy: String,
        val ipv6: String,
    )

    private data class UfwConfValues(
        val logLevel: String,
    )

    private fun readDefaultConfigValues(): DefaultConfigValues {
        val path = Path.of(ufwProps.defaultConfigPath)
        if (!path.exists()) {
            return DefaultConfigValues("deny", "allow", "deny", "yes")
        }

        val content = path.readText()
        return DefaultConfigValues(
            inputPolicy = UfwConfigTemplates.policyFromUfwFileValue(
                readConfigValue(content, "DEFAULT_INPUT_POLICY") ?: "DROP",
            ),
            outputPolicy = UfwConfigTemplates.policyFromUfwFileValue(
                readConfigValue(content, "DEFAULT_OUTPUT_POLICY") ?: "ACCEPT",
            ),
            forwardPolicy = UfwConfigTemplates.policyFromUfwFileValue(
                readConfigValue(content, "DEFAULT_FORWARD_POLICY") ?: "DROP",
            ),
            ipv6 = readConfigValue(content, "IPV6")?.lowercase() ?: "yes",
        )
    }

    private fun readUfwConfigValues(): UfwConfValues {
        val path = Path.of(ufwProps.ufwConfigPath)
        if (!path.exists()) {
            return UfwConfValues("low")
        }

        val content = path.readText()
        return UfwConfValues(
            logLevel = readConfigValue(content, "LOGLEVEL")?.lowercase() ?: "low",
        )
    }

    private fun readConfigValue(content: String, key: String): String? =
        Regex("""^$key=(.+)$""", RegexOption.MULTILINE)
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.trim('"')

    private fun writeDefaultConfig(values: Map<String, String>) {
        val path = Path.of(ufwProps.defaultConfigPath)
        val content = if (path.exists()) path.readText() else defaultUfwTemplate()

        val updated = updateConfigValue(
            updateConfigValue(
                updateConfigValue(
                    updateConfigValue(
                        content,
                        "DEFAULT_INPUT_POLICY",
                        UfwConfigTemplates.policyToUfwFileValue(values["defaultIncoming"].orEmpty()),
                    ),
                    "DEFAULT_OUTPUT_POLICY",
                    UfwConfigTemplates.policyToUfwFileValue(values["defaultOutgoing"].orEmpty()),
                ),
                "DEFAULT_FORWARD_POLICY",
                UfwConfigTemplates.policyToUfwFileValue(values["defaultForward"].orEmpty()),
            ),
            "IPV6",
            normalizeYesNo(values["ipv6"].orEmpty()),
        )

        path.writeText(updated)
    }

    private fun writeUfwConfig(values: Map<String, String>) {
        val path = Path.of(ufwProps.ufwConfigPath)
        val content = if (path.exists()) path.readText() else ufwConfTemplate()
        val updated = updateConfigValue(content, "LOGLEVEL", values["logLevel"].orEmpty().lowercase())
        Files.createDirectories(path.parent)
        path.writeText(updated)
    }

    private fun applyEnabledState(enabled: String): AppOperationResult {
        val shouldEnable = normalizeYesNo(enabled) == "yes"
        val current = detectServiceState()

        if (shouldEnable && current != ServiceState.RUNNING) {
            val result = commandExecutor.runShell("ufw --force enable 2>&1")
            return if (result.success) {
                AppOperationResult(true, "UFW включён")
            } else {
                AppOperationResult(false, "Не удалось включить UFW: ${result.output}")
            }
        }

        if (!shouldEnable && current == ServiceState.RUNNING) {
            val result = commandExecutor.runShell("ufw disable 2>&1")
            return if (result.success) {
                AppOperationResult(true, "UFW отключён")
            } else {
                AppOperationResult(false, "Не удалось отключить UFW: ${result.output}")
            }
        }

        return AppOperationResult(true, "Состояние UFW без изменений")
    }

    private fun validateSettings(values: Map<String, String>): String? {
        val policies = listOf("defaultIncoming", "defaultOutgoing", "defaultForward")
        for (key in policies) {
            val normalized = UfwConfigTemplates.normalizePolicy(values[key].orEmpty())
            if (normalized !in setOf("deny", "allow", "reject")) {
                return "Недопустимое значение политики: ${values[key]}"
            }
        }

        val ipv6 = normalizeYesNo(values["ipv6"].orEmpty())
        if (ipv6 !in setOf("yes", "no")) {
            return "IPv6 должен быть yes или no"
        }

        val enabled = normalizeYesNo(values["enabled"].orEmpty())
        if (enabled !in setOf("yes", "no")) {
            return "Поле «Брандмауэр включён» должно быть yes или no"
        }

        val logLevel = values["logLevel"].orEmpty().lowercase()
        if (logLevel !in setOf("off", "low", "medium", "high", "full")) {
            return "Недопустимый уровень логирования: $logLevel"
        }

        return UfwPortRules.validateUserInput(values["openPorts"].orEmpty())
    }

    private fun normalizeYesNo(value: String): String =
        when (value.trim().lowercase()) {
            "yes", "true", "1", "on" -> "yes"
            else -> "no"
        }

    private fun updateConfigValue(content: String, key: String, value: String): String {
        val pattern = Regex("""^$key=.*$""", RegexOption.MULTILINE)
        val line = "$key=$value"
        return if (pattern.containsMatchIn(content)) {
            pattern.replace(content, line)
        } else {
            content.trimEnd() + "\n$line\n"
        }
    }

    private fun defaultUfwTemplate(): String = """
# Managed by Kern
IPV6=yes
DEFAULT_INPUT_POLICY="DROP"
DEFAULT_OUTPUT_POLICY="ACCEPT"
DEFAULT_FORWARD_POLICY="DROP"
""".trimIndent() + "\n"

    private fun ufwConfTemplate(): String = """
# Managed by Kern
ENABLED=no
LOGLEVEL=low
""".trimIndent() + "\n"
}
