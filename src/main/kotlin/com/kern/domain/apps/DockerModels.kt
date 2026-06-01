package com.kern.domain.apps

data class RunningContainer(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val ports: String?,
)
