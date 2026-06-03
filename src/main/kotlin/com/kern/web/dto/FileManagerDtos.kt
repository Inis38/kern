package com.kern.web.dto

import com.kern.domain.files.DirectoryListing
import com.kern.domain.files.FileContent
import com.kern.domain.files.FileEntry
import com.kern.domain.files.FileEntryType
import com.kern.domain.files.FileOperationResult
data class DirectoryListingResponse(
    val path: String,
    val parentPath: String?,
    val entries: List<FileEntryResponse>,
)

data class FileEntryResponse(
    val name: String,
    val path: String,
    val type: String,
    val typeLabel: String,
    val sizeBytes: Long?,
    val sizeLabel: String,
    val modifiedAt: String?,
    val readable: Boolean,
    val writable: Boolean,
)

data class FileContentResponse(
    val path: String,
    val content: String,
    val truncated: Boolean,
    val sizeBytes: Long,
    val sizeLabel: String,
)

data class DeleteFilesRequest(
    val paths: List<String>,
)

fun DirectoryListing.toResponse(): DirectoryListingResponse =
    DirectoryListingResponse(
        path = path,
        parentPath = parentPath,
        entries = entries.map { it.toResponse() },
    )

fun FileContent.toResponse(): FileContentResponse =
    FileContentResponse(
        path = path,
        content = content,
        truncated = truncated,
        sizeBytes = sizeBytes,
        sizeLabel = formatSize(sizeBytes),
    )

fun FileOperationResult.toResponse(): OperationResponse =
    OperationResponse(success = success, message = message)

private fun FileEntry.toResponse() = FileEntryResponse(
    name = name,
    path = path,
    type = type.name.lowercase(),
    typeLabel = type.toLabel(),
    sizeBytes = sizeBytes,
    sizeLabel = sizeBytes?.let { formatSize(it) } ?: "—",
    modifiedAt = modifiedAt?.toString(),
    readable = readable,
    writable = writable,
)

private fun FileEntryType.toLabel(): String =
    when (this) {
        FileEntryType.DIRECTORY -> "Каталог"
        FileEntryType.FILE -> "Файл"
        FileEntryType.SYMLINK -> "Ссылка"
        FileEntryType.OTHER -> "Другое"
    }

private fun formatSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> String.format("%.1f КБ", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f МБ", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f ГБ", bytes / (1024.0 * 1024.0 * 1024.0))
    }
