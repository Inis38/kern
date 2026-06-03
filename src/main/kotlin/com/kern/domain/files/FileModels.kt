package com.kern.domain.files

import java.time.Instant

enum class FileEntryType {
    DIRECTORY,
    FILE,
    SYMLINK,
    OTHER,
}

data class FileEntry(
    val name: String,
    val path: String,
    val type: FileEntryType,
    val sizeBytes: Long?,
    val modifiedAt: Instant?,
    val readable: Boolean,
    val writable: Boolean,
)

data class DirectoryListing(
    val path: String,
    val parentPath: String?,
    val entries: List<FileEntry>,
)

data class FileContent(
    val path: String,
    val content: String,
    val truncated: Boolean,
    val sizeBytes: Long,
)

data class FileOperationResult(
    val success: Boolean,
    val message: String,
)

class FileManagerException(
    val code: String,
    override val message: String,
) : RuntimeException(message)
