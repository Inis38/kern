package com.kern.infrastructure.apps.nginx

import com.kern.config.AppsProperties
import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppConfigFieldDefinition
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.ConfigFieldType
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.ServiceState
import com.kern.domain.port.ManagedApplication
import com.kern.infrastructure.process.ProcessResult
import com.kern.infrastructure.process.CommandExecutor
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.writeText

@Component
class NginxManagedApplication(
    private val commandExecutor: CommandExecutor,
    private val appsProperties: AppsProperties,
) : ManagedApplication {

    override val id = ManagedAppId.NGINX
    override val description = "Веб-сервер и reverse proxy"

    private val nginxProps get() = appsProperties.nginx

    override fun status(): ManagedAppStatus {
        val versionResult = commandExecutor.runShell("nginx -v 2>&1")
        val installed = versionResult.exitCode == 0 ||
            commandExecutor.runShell("command -v nginx").success

        if (!installed) {
            return ManagedAppStatus(
                appId = id,
                installState = InstallState.NOT_INSTALLED,
                serviceState = ServiceState.UNKNOWN,
                version = null,
                detail = "Nginx не найден в системе",
            )
        }

        val serviceState = detectServiceState()
        return ManagedAppStatus(
            appId = id,
            installState = InstallState.INSTALLED,
            serviceState = serviceState,
            version = extractVersion(versionResult.output),
            detail = serviceDetail(serviceState),
        )
    }

    override fun install(): AppOperationResult {
        if (status().installState == InstallState.INSTALLED) {
            return AppOperationResult(true, "Nginx уже установлен")
        }

        if (!commandExecutor.runShell("command -v apt-get").success) {
            return AppOperationResult(
                false,
                "Установка поддерживается только на Debian/Ubuntu (apt-get)",
            )
        }

        val install = commandExecutor.runShell(
            "export DEBIAN_FRONTEND=noninteractive && " +
                "apt-get update -qq && apt-get install -y -qq nginx",
        )
        if (!install.success) {
            return AppOperationResult(false, "Ошибка установки: ${install.output.take(500)}")
        }

        ensureDefaultConfig()
        val reload = reloadOrStart()
        val message = if (reload.success) "Nginx успешно установлен" else "Установлен, запуск: ${reload.output}"
        return AppOperationResult(reload.success, message)
    }

    override fun configDefinitions(): List<AppConfigFieldDefinition> =
        NginxConfigTemplates.definitions.map { (key, def) ->
            AppConfigFieldDefinition(
                key = key,
                label = def.label,
                description = def.description,
                section = def.section,
            )
        }

    override fun loadConfig(): List<AppConfigField> {
        val values = readSettings()
        return configDefinitions().map { def ->
            AppConfigField(
                key = def.key,
                label = def.label,
                type = def.type,
                value = values[def.key] ?: NginxConfigTemplates.defaultValues[def.key].orEmpty(),
                description = def.description,
                required = def.required,
                section = def.section,
            )
        }
    }

    override fun saveConfig(values: Map<String, String>): AppOperationResult {
        if (status().installState != InstallState.INSTALLED) {
            return AppOperationResult(false, "Сначала установите Nginx")
        }

        val merged = NginxConfigTemplates.defaultValues.toMutableMap()
        merged.putAll(values)
        writeSettings(merged)
        applySiteConfig(merged)

        val test = commandExecutor.runShell("nginx -t 2>&1")
        if (!test.success) {
            return AppOperationResult(false, "Проверка конфигурации не пройдена: ${test.output}")
        }

        val reload = reloadOrStart()
        return if (reload.success) {
            AppOperationResult(true, "Конфигурация сохранена и применена")
        } else {
            AppOperationResult(false, "Сохранено, но перезагрузка не удалась: ${reload.output}")
        }
    }

    private fun ensureDefaultConfig() {
        if (!Path.of(nginxProps.settingsPath).exists()) {
            writeSettings(NginxConfigTemplates.defaultValues)
            applySiteConfig(NginxConfigTemplates.defaultValues)
        }
    }

    private fun applySiteConfig(values: Map<String, String>) {
        val sitePath = Path.of(nginxProps.siteConfigPath)
        Files.createDirectories(sitePath.parent)
        sitePath.writeText(NginxConfigTemplates.siteConfig(values))

        val enabledLink = Path.of(nginxProps.enabledLinkPath)
        if (!enabledLink.exists()) {
            Files.createSymbolicLink(enabledLink, sitePath)
        }
    }

    private fun readSettings(): Map<String, String> {
        val path = Path.of(nginxProps.settingsPath)
        if (!path.exists()) return NginxConfigTemplates.defaultValues

        val props = Properties()
        Files.newInputStream(path).use { props.load(it) }
        return props.stringPropertyNames().associateWith { props.getProperty(it).orEmpty() }
    }

    private fun writeSettings(values: Map<String, String>) {
        val path = Path.of(nginxProps.settingsPath)
        Files.createDirectories(path.parent)
        val props = Properties()
        values.forEach { (k, v) -> props.setProperty(k, v) }
        Files.newOutputStream(path).use { props.store(it, "Kern nginx settings") }
    }

    private fun reloadOrStart(): ProcessResult {
        val active = commandExecutor.runShell("systemctl is-active nginx 2>/dev/null")
        if (active.success && active.output.trim() == "active") {
            return commandExecutor.runShell("systemctl reload nginx 2>&1")
        }
        val start = commandExecutor.runShell("systemctl start nginx 2>&1")
        if (start.success) return start
        return commandExecutor.runShell("nginx -s reload 2>&1 || nginx 2>&1")
    }

    private fun detectServiceState(): ServiceState {
        val systemd = commandExecutor.runShell("systemctl is-active nginx 2>/dev/null")
        if (systemd.output.trim() == "active") return ServiceState.RUNNING
        if (systemd.output.trim() == "inactive") return ServiceState.STOPPED
        return if (commandExecutor.runShell("pgrep -x nginx").success) {
            ServiceState.RUNNING
        } else {
            ServiceState.STOPPED
        }
    }

    private fun extractVersion(output: String): String? =
        Regex("nginx/(\\S+)").find(output)?.groupValues?.get(1)

    private fun serviceDetail(state: ServiceState): String = when (state) {
        ServiceState.RUNNING -> "Служба запущена"
        ServiceState.STOPPED -> "Установлен, но не запущен"
        ServiceState.UNKNOWN -> "Состояние службы неизвестно"
    }
}
