package com.kern.infrastructure.files

import com.kern.config.FileManagerProperties
import com.kern.domain.files.DirectoryListing
import com.kern.domain.files.FileContent
import com.kern.domain.files.FileEntry
import com.kern.domain.files.FileEntryType
import com.kern.domain.files.FileManagerException
import com.kern.domain.files.FileOperationResult
import com.kern.domain.port.FileManager
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

@Component
class LocalFileManager(
    private val properties: FileManagerProperties,
    private val pathGuard: FilePathGuard,
) : FileManager {

    override fun defaultDirectory(): String = pathGuard.defaultDirectory()

    override fun listDirectory(path: String?): DirectoryListing {
        val dir = pathGuard.resolve(path)
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw FileManagerException("NOT_DIRECTORY", "Указанный путь не является каталогом")
        }
        pathGuard.assertReadable(dir)

        val entries = Files.list(dir).use { stream ->
            stream.map { toFileEntry(it) }.toList()
        }.sortedWith(
            compareBy<FileEntry> { it.type != FileEntryType.DIRECTORY }.thenBy { it.name.lowercase() },
        )

        return DirectoryListing(
            path = dir.toString(),
            parentPath = pathGuard.parentPath(dir),
            entries = entries,
        )
    }

    override fun readContent(path: String): FileContent {
        val file = pathGuard.resolveRequired(path)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw FileManagerException("NOT_FILE", "Просмотр доступен только для обычных файлов")
        }
        pathGuard.assertReadable(file)

        val size = Files.size(file)
        val maxBytes = properties.maxContentBytes
        val readBytes = Files.newInputStream(file).use { input ->
            input.readNBytes(minOf(size, maxBytes).toInt())
        }
        if (readBytes.contains(0)) {
            throw FileManagerException("BINARY", "Просмотр доступен только для текстовых файлов")
        }

        return FileContent(
            path = file.toString(),
            content = String(readBytes, StandardCharsets.UTF_8),
            truncated = size > readBytes.size,
            sizeBytes = size,
        )
    }

    override fun resolveDownloadPath(path: String): Path {
        val file = pathGuard.resolveRequired(path)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw FileManagerException("NOT_FILE", "Скачивание доступно только для файлов")
        }
        pathGuard.assertReadable(file)
        return file
    }

    override fun upload(
        targetDirectory: String,
        fileName: String,
        content: InputStream,
        sizeBytes: Long,
    ): FileOperationResult {
        if (sizeBytes > properties.maxUploadBytes) {
            throw FileManagerException(
                "TOO_LARGE",
                "Файл превышает лимит ${properties.maxUploadBytes / (1024 * 1024)} МБ",
            )
        }

        val dir = pathGuard.resolve(targetDirectory)
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw FileManagerException("NOT_DIRECTORY", "Каталог назначения не найден")
        }
        pathGuard.assertWritable(dir)

        val safeName = pathGuard.sanitizeFileName(fileName)
        val target = dir.resolve(safeName)
        pathGuard.resolveRequired(target.toString())

        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING)
        return FileOperationResult(true, "Файл «$safeName» загружен")
    }

    override fun delete(paths: List<String>): FileOperationResult {
        if (paths.isEmpty()) {
            throw FileManagerException("EMPTY", "Не выбраны файлы для удаления")
        }

        var deleted = 0
        val errors = mutableListOf<String>()

        for (rawPath in paths.distinct()) {
            try {
                deleteOne(rawPath)
                deleted++
            } catch (e: FileManagerException) {
                errors.add(e.message)
            }
        }

        return when {
            deleted == 0 -> FileOperationResult(false, errors.joinToString("; "))
            errors.isEmpty() -> FileOperationResult(true, "Удалено элементов: $deleted")
            else -> FileOperationResult(
                true,
                "Удалено: $deleted. Ошибки: ${errors.joinToString("; ")}",
            )
        }
    }

    private fun deleteOne(path: String) {
        val target = pathGuard.resolveRequired(path)
        pathGuard.assertNotProtectedRoot(target)

        when {
            Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) -> {
                pathGuard.assertWritable(target)
                if (Files.list(target).findAny().isPresent) {
                    throw FileManagerException("NOT_EMPTY", "Каталог не пуст: ${target.fileName}")
                }
                Files.delete(target)
            }
            Files.exists(target, LinkOption.NOFOLLOW_LINKS) -> {
                pathGuard.assertWritable(target.parent ?: target)
                Files.delete(target)
            }
            else -> throw FileManagerException("NOT_FOUND", "Файл не найден: $path")
        }
    }

    private fun toFileEntry(path: Path): FileEntry {
        val type = entryType(path)
        val attrs = try {
            Files.readAttributes(
                path,
                java.nio.file.attribute.BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
        } catch (_: Exception) {
            null
        }

        return FileEntry(
            name = path.fileName.toString(),
            path = path.toAbsolutePath().normalize().toString(),
            type = type,
            sizeBytes = if (type == FileEntryType.FILE) attrs?.size() else null,
            modifiedAt = attrs?.lastModifiedTime()?.toInstant(),
            readable = Files.isReadable(path),
            writable = Files.isWritable(path),
        )
    }

    private fun entryType(path: Path): FileEntryType =
        when {
            Files.isSymbolicLink(path) -> FileEntryType.SYMLINK
            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> FileEntryType.DIRECTORY
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> FileEntryType.FILE
            else -> FileEntryType.OTHER
        }
}
