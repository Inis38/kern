package com.kern.infrastructure.apps.docker

import com.kern.domain.apps.AppConfigField
import com.kern.domain.apps.AppConfigFieldDefinition
import com.kern.domain.apps.AppOperationResult
import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ManagedAppId
import com.kern.domain.apps.ManagedAppStatus
import com.kern.domain.apps.RunningContainer
import com.kern.domain.apps.ServiceState
import com.kern.domain.port.ManagedApplication
import com.kern.domain.port.RunningContainerProvider
import com.kern.infrastructure.process.CommandExecutor
import org.springframework.stereotype.Component

@Component
class DockerManagedApplication(
    private val commandExecutor: CommandExecutor,
) : ManagedApplication, RunningContainerProvider {

    override val id = ManagedAppId.DOCKER
    override val description = "Контейнеризация и управление контейнерами"

    override fun status(): ManagedAppStatus {
        if (!isInstalled()) {
            return ManagedAppStatus(
                appId = id,
                installState = InstallState.NOT_INSTALLED,
                serviceState = ServiceState.UNKNOWN,
                version = null,
                detail = "Docker не найден в системе",
            )
        }

        val version = extractVersion(commandExecutor.runShell("docker --version 2>&1").output)
        val serviceState = detectServiceState()
        val runningCount = if (serviceState == ServiceState.RUNNING) {
            listRunningContainers().size
        } else {
            0
        }

        return ManagedAppStatus(
            appId = id,
            installState = InstallState.INSTALLED,
            serviceState = serviceState,
            version = version,
            detail = serviceDetail(serviceState, runningCount),
        )
    }

    override fun install(): AppOperationResult {
        if (status().installState == InstallState.INSTALLED) {
            return AppOperationResult(true, "Docker уже установлен")
        }

        if (!commandExecutor.runShell("command -v apt-get").success) {
            return AppOperationResult(
                false,
                "Установка поддерживается только на Debian/Ubuntu (apt-get)",
            )
        }

        val install = commandExecutor.runShell(
            "export DEBIAN_FRONTEND=noninteractive && " +
                "apt-get update -qq && apt-get install -y -qq docker.io",
        )
        if (!install.success) {
            return AppOperationResult(false, "Ошибка установки: ${install.output.take(500)}")
        }

        val enable = commandExecutor.runShell("systemctl enable --now docker 2>&1")
        val message = if (enable.success) {
            "Docker успешно установлен и запущен"
        } else {
            "Docker установлен, запуск службы: ${enable.output.take(300)}"
        }
        return AppOperationResult(enable.success, message)
    }

    override fun configDefinitions(): List<AppConfigFieldDefinition> = emptyList()

    override fun loadConfig(): List<AppConfigField> = emptyList()

    override fun saveConfig(values: Map<String, String>): AppOperationResult =
        AppOperationResult(false, "Для Docker нет настраиваемых параметров в интерфейсе")

    override fun listRunningContainers(): List<RunningContainer> {
        if (!isInstalled() || detectServiceState() != ServiceState.RUNNING) {
            return emptyList()
        }

        val result = commandExecutor.runShell(
            "docker ps --format '{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' 2>&1",
        )
        if (!result.success) {
            return emptyList()
        }

        return DockerContainers.parsePsOutput(result.output)
    }

    private fun isInstalled(): Boolean =
        commandExecutor.runShell("command -v docker").success

    private fun detectServiceState(): ServiceState {
        val systemd = commandExecutor.runShell("systemctl is-active docker 2>/dev/null")
        when (systemd.output.trim()) {
            "active" -> return ServiceState.RUNNING
            "inactive", "failed" -> return ServiceState.STOPPED
        }

        val info = commandExecutor.runShell("docker info >/dev/null 2>&1")
        return if (info.success) ServiceState.RUNNING else ServiceState.STOPPED
    }

    private fun extractVersion(output: String): String? =
        Regex("Docker version\\s+(\\S+)", RegexOption.IGNORE_CASE)
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.trimEnd(',')

    private fun serviceDetail(state: ServiceState, runningCount: Int): String = when (state) {
        ServiceState.RUNNING -> when (runningCount) {
            0 -> "Демон запущен, запущенных контейнеров нет"
            1 -> "Демон запущен, 1 контейнер"
            in 2..4 -> "Демон запущен, $runningCount контейнера"
            else -> "Демон запущен, $runningCount контейнеров"
        }
        ServiceState.STOPPED -> "Установлен, демон не запущен"
        ServiceState.UNKNOWN -> "Состояние демона неизвестно"
    }
}
