package com.kern.application

import com.kern.domain.files.DirectoryListing
import com.kern.domain.files.FileContent
import com.kern.domain.files.FileOperationResult
import com.kern.domain.port.FileManager
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Path

@Service
class FileManagerService(
    private val fileManager: FileManager,
) {
    fun defaultDirectory(): String = fileManager.defaultDirectory()

    fun list(path: String?): DirectoryListing = fileManager.listDirectory(path)

    fun content(path: String): FileContent = fileManager.readContent(path)

    fun downloadPath(path: String): Path = fileManager.resolveDownloadPath(path)

    fun upload(targetDirectory: String, fileName: String, content: InputStream, sizeBytes: Long): FileOperationResult =
        fileManager.upload(targetDirectory, fileName, content, sizeBytes)

    fun delete(paths: List<String>): FileOperationResult = fileManager.delete(paths)
}
