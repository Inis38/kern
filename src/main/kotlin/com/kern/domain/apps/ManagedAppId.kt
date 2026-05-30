package com.kern.domain.apps

enum class ManagedAppId(val slug: String, val displayName: String) {
    NGINX("nginx", "Nginx"),
    ;

    companion object {
        fun fromSlug(slug: String): ManagedAppId? =
            entries.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
    }
}
