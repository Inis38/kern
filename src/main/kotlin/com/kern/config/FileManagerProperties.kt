package com.kern.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kern.files")
data class FileManagerProperties(
    /** Корневые каталоги, внутри которых разрешена навигация (абсолютные пути). */
    val allowedRoots: List<String> = listOf("/"),
    /** Максимальный размер файла для просмотра в браузере (байты). */
    val maxContentBytes: Long = 512 * 1024,
    /** Максимальный размер загружаемого файла (байты). */
    val maxUploadBytes: Long = 100 * 1024 * 1024,
)
