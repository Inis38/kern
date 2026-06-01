package com.kern.infrastructure.apps.docker

import com.kern.domain.apps.RunningContainer

object DockerContainers {

    fun parsePsOutput(output: String): List<RunningContainer> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@mapNotNull null
                RunningContainer(
                    id = parts[0],
                    name = parts[1],
                    image = parts[2],
                    status = parts[3],
                    ports = parts.getOrNull(4)?.takeIf { it.isNotBlank() && it != "-" },
                )
            }
            .toList()
}
