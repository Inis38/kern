package com.kern.infrastructure.apps

import com.kern.domain.apps.ManagedAppId
import com.kern.domain.port.ManagedApplication
import org.springframework.stereotype.Component

@Component
class ManagedApplicationRegistry(
    applications: List<ManagedApplication>,
) {
    private val byId: Map<ManagedAppId, ManagedApplication> =
        applications.associateBy { it.id }

    fun all(): List<ManagedApplication> =
        ManagedAppId.entries.mapNotNull { byId[it] }

    fun get(id: ManagedAppId): ManagedApplication? = byId[id]

    fun get(slug: String): ManagedApplication? =
        ManagedAppId.fromSlug(slug)?.let { byId[it] }
}
