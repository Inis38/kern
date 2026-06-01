package com.kern.infrastructure.process

interface CommandExecutor {
    fun runShell(command: String): ProcessResult
}
