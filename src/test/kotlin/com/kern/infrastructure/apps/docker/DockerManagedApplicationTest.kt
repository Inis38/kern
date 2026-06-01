package com.kern.infrastructure.apps.docker

import com.kern.domain.apps.InstallState
import com.kern.domain.apps.ServiceState
import com.kern.infrastructure.process.CommandExecutor
import com.kern.infrastructure.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockerManagedApplicationTest {

    @Test
    fun `status reports not installed when docker binary missing`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when {
                    cmd == "command -v docker" -> failure()
                    else -> failure()
                }
            },
        )

        val status = app.status()

        assertEquals(InstallState.NOT_INSTALLED, status.installState)
        assertEquals(ServiceState.UNKNOWN, status.serviceState)
        assertEquals(null, status.version)
        assertEquals("Docker не найден в системе", status.detail)
    }

    @Test
    fun `status reports running daemon with version and container count`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when {
                    cmd == "command -v docker" -> ok()
                    cmd == "docker --version 2>&1" -> ok("Docker version 27.5.1, build abc")
                    cmd == "systemctl is-active docker 2>/dev/null" -> ok("active")
                    cmd.contains("docker ps --format") -> ok("c1\tweb\tnginx:alpine\tUp 1 hour\t0.0.0.0:80->80/tcp")
                    else -> failure()
                }
            },
        )

        val status = app.status()

        assertEquals(InstallState.INSTALLED, status.installState)
        assertEquals(ServiceState.RUNNING, status.serviceState)
        assertEquals("27.5.1", status.version)
        assertEquals("Демон запущен, 1 контейнер", status.detail)
    }

    @Test
    fun `status reports stopped daemon`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when {
                    cmd == "command -v docker" -> ok()
                    cmd == "docker --version 2>&1" -> ok("Docker version 26.0.0")
                    cmd == "systemctl is-active docker 2>/dev/null" -> ok("inactive")
                    cmd == "docker info >/dev/null 2>&1" -> failure()
                    else -> failure()
                }
            },
        )

        val status = app.status()

        assertEquals(ServiceState.STOPPED, status.serviceState)
        assertEquals("Установлен, демон не запущен", status.detail)
    }

    @Test
    fun `status reports zero containers when none running`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when {
                    cmd == "command -v docker" -> ok()
                    cmd == "docker --version 2>&1" -> ok("Docker version 27.0.0")
                    cmd == "systemctl is-active docker 2>/dev/null" -> ok("active")
                    cmd.contains("docker ps --format") -> ok("")
                    else -> failure()
                }
            },
        )

        val status = app.status()

        assertEquals("Демон запущен, запущенных контейнеров нет", status.detail)
    }

    @Test
    fun `listRunningContainers returns parsed containers when daemon active`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when {
                    cmd == "command -v docker" -> ok()
                    cmd == "systemctl is-active docker 2>/dev/null" -> ok("active")
                    cmd.contains("docker ps --format") ->
                        ok("abc\tmy-app\tnginx:latest\tUp 2 hours\t8080:80")
                    else -> failure()
                }
            },
        )

        val containers = app.listRunningContainers()

        assertEquals(1, containers.size)
        assertEquals("abc", containers[0].id)
        assertEquals("my-app", containers[0].name)
        assertEquals("nginx:latest", containers[0].image)
    }

    @Test
    fun `listRunningContainers returns empty when daemon stopped`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when {
                    cmd == "command -v docker" -> ok()
                    cmd == "systemctl is-active docker 2>/dev/null" -> ok("inactive")
                    cmd == "docker info >/dev/null 2>&1" -> failure()
                    else -> failure()
                }
            },
        )

        assertTrue(app.listRunningContainers().isEmpty())
    }

    @Test
    fun `install returns success when already installed`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when {
                    cmd == "command -v docker" -> ok()
                    cmd == "docker --version 2>&1" -> ok("Docker version 27.0.0")
                    cmd == "systemctl is-active docker 2>/dev/null" -> ok("active")
                    cmd.contains("docker ps --format") -> ok("")
                    else -> failure()
                }
            },
        )

        val result = app.install()

        assertTrue(result.success)
        assertEquals("Docker уже установлен", result.message)
    }

    @Test
    fun `install fails without apt-get`() {
        val app = dockerApp(
            onCommand = { cmd ->
                when (cmd) {
                    "command -v docker" -> failure()
                    "command -v apt-get" -> failure()
                    else -> failure()
                }
            },
        )

        val result = app.install()

        assertFalse(result.success)
        assertTrue(result.message.contains("apt-get"))
    }

    @Test
    fun `saveConfig reports no configurable parameters`() {
        val app = dockerApp(onCommand = { failure() })

        val result = app.saveConfig(emptyMap())

        assertFalse(result.success)
        assertTrue(result.message.contains("нет настраиваемых параметров"))
    }

    private fun dockerApp(onCommand: (String) -> ProcessResult): DockerManagedApplication =
        DockerManagedApplication(
            object : CommandExecutor {
                override fun runShell(command: String): ProcessResult = onCommand(command)

                override fun runShell(command: String, timeoutSeconds: Long): ProcessResult =
                    onCommand(command)
            },
        )

    private fun ok(output: String = "") = ProcessResult(exitCode = 0, output = output)

    private fun failure() = ProcessResult(exitCode = 1, output = "")
}
