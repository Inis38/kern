package com.kern.infrastructure.process

import com.kern.config.AppsProperties
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

data class ProcessResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
) {
    val success: Boolean get() = !timedOut && exitCode == 0
}

@Component
class ProcessRunner(
    private val appsProperties: AppsProperties,
) : CommandExecutor {

    override fun runShell(command: String): ProcessResult =
        runShell(command, appsProperties.commandTimeoutSeconds)

    override fun runShell(command: String, timeoutSeconds: Long): ProcessResult {
        val process = ProcessBuilder(listOf("/bin/bash", "-c", command))
            .redirectErrorStream(true)
            .start()

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText().trim()

        if (!finished) {
            process.destroyForcibly()
            return ProcessResult(exitCode = -1, output = output, timedOut = true)
        }

        return ProcessResult(exitCode = process.exitValue(), output = output)
    }
}
