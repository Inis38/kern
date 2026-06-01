package com.kern.infrastructure.apps.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DockerContainersTest {

    @Test
    fun `parsePsOutput parses docker ps lines`() {
        val output = """
            abc123def456	my-app	nginx:alpine	Up 2 hours	0.0.0.0:8080->80/tcp
            fed987654321	redis	redis:7	Up 5 minutes	-
        """.trimIndent()

        val containers = DockerContainers.parsePsOutput(output)

        assertEquals(2, containers.size)
        assertEquals("abc123def456", containers[0].id)
        assertEquals("my-app", containers[0].name)
        assertEquals("nginx:alpine", containers[0].image)
        assertEquals("Up 2 hours", containers[0].status)
        assertEquals("0.0.0.0:8080->80/tcp", containers[0].ports)
        assertNull(containers[1].ports)
    }

    @Test
    fun `parsePsOutput skips malformed lines`() {
        val containers = DockerContainers.parsePsOutput("incomplete\tline\n")
        assertEquals(0, containers.size)
    }
}
