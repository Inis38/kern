package com.kern.domain.port

import com.kern.domain.files.DirectoryListing
import com.kern.domain.files.FileContent
import com.kern.domain.files.FileOperationResult
import java.io.InputStream
import java.nio.file.Path

interface FileManager {
    fun defaultDirectory(): String

    fun listDirectory(path: String?): DirectoryListing

    fun readContent(path: String): FileContent

    fun resolveDownloadPath(path: String): Path

    fun upload(targetDirectory: String, fileName: String, content: InputStream, sizeBytes: Long): FileOperationResult

    fun delete(paths: List<String>): FileOperationResult
}
