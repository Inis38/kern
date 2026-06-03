package com.kern.infrastructure.files

import com.kern.config.FileManagerProperties
import com.kern.domain.files.FileManagerException
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

@Component
class FilePathGuard(
    private val properties: FileManagerProperties,
) {
    private val allowedRoots: List<Path> by lazy {
        properties.allowedRoots
            .map { normalizeRoot(Paths.get(it)) }
            .map { realPath(it) }
            .distinct()
    }

    fun resolve(path: String?): Path {
        val candidate = when {
            path.isNullOrBlank() -> defaultRoot()
            else -> Paths.get(path)
        }
        return resolveWithinRoots(candidate)
    }

    fun resolveRequired(path: String): Path = resolve(path)

    fun parentPath(path: Path): String? {
        val parent = path.parent ?: return null
        return try {
            resolveWithinRoots(parent).toString()
        } catch (_: FileManagerException) {
            null
        }
    }

    fun defaultDirectory(): String = defaultRoot().toString()

    private fun defaultRoot(): Path = allowedRoots.minBy { it.nameCount }

    private fun normalizeRoot(root: Path): Path {
        val absolute = root.toAbsolutePath().normalize()
        if (!Files.exists(absolute)) {
            throw FileManagerException("ROOT_MISSING", "Корневой каталог не существует: $absolute")
        }
        return absolute
    }

    private fun resolveWithinRoots(userPath: Path): Path {
        val absolute = userPath.toAbsolutePath().normalize()
        val resolved = realPath(absolute)
        if (!isUnderAllowedRoot(resolved)) {
            throw FileManagerException("ACCESS_DENIED", "Доступ к каталогу запрещён")
        }
        return resolved
    }

    private fun isUnderAllowedRoot(path: Path): Boolean {
        val resolved = realPath(path)
        return allowedRoots.any { root -> resolved.startsWith(root) }
    }

    private fun realPath(path: Path): Path =
        try {
            path.toRealPath()
        } catch (_: Exception) {
            path.toAbsolutePath().normalize()
        }

    fun assertWritable(path: Path) {
        if (!Files.isWritable(path)) {
            throw FileManagerException("NOT_WRITABLE", "Нет прав на запись: $path")
        }
    }

    fun assertReadable(path: Path) {
        if (!Files.isReadable(path)) {
            throw FileManagerException("NOT_READABLE", "Нет прав на чтение: $path")
        }
    }

    fun sanitizeFileName(fileName: String): String {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") {
            throw FileManagerException("INVALID_NAME", "Недопустимое имя файла")
        }
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains('\u0000')) {
            throw FileManagerException("INVALID_NAME", "Имя файла не должно содержать разделители пути")
        }
        return trimmed
    }

    fun assertNotProtectedRoot(path: Path) {
        if (allowedRoots.any { it == path }) {
            throw FileManagerException("PROTECTED", "Нельзя удалить корневой каталог")
        }
    }
}
