package com.kern.domain.port

import com.kern.domain.apps.RunningContainer

interface RunningContainerProvider {
    fun listRunningContainers(): List<RunningContainer>
}
