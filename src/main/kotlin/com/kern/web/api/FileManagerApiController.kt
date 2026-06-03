package com.kern.web.api

import com.kern.application.FileManagerService
import com.kern.domain.files.FileManagerException
import com.kern.web.dto.DeleteFilesRequest
import com.kern.web.dto.DirectoryListingResponse
import com.kern.web.dto.FileContentResponse
import com.kern.web.dto.OperationResponse
import com.kern.web.dto.toResponse
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/v1/files")
class FileManagerApiController(
    private val fileManagerService: FileManagerService,
) {

    @GetMapping
    fun list(@RequestParam(required = false) path: String?): DirectoryListingResponse =
        fileManagerService.list(path).toResponse()

    @GetMapping("/content")
    fun content(@RequestParam path: String): FileContentResponse =
        fileManagerService.content(path).toResponse()

    @GetMapping("/download")
    fun download(@RequestParam path: String): ResponseEntity<Resource> {
        val filePath = fileManagerService.downloadPath(path)
        val resource = FileSystemResource(filePath)
        if (!resource.exists()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден")
        }
        val fileName = filePath.fileName.toString()
        val encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$encoded")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestParam path: String,
        @RequestParam("file") file: MultipartFile,
    ): OperationResponse {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не выбран")
        }
        val originalName = file.originalFilename?.substringAfterLast('/')?.substringAfterLast('\\')
            ?: "upload.bin"
        return file.inputStream.use { stream ->
            fileManagerService.upload(path, originalName, stream, file.size)
        }.toResponse()
    }

    @DeleteMapping
    fun delete(@RequestBody body: DeleteFilesRequest): OperationResponse =
        fileManagerService.delete(body.paths).toResponse()

    @ExceptionHandler(FileManagerException::class)
    fun handleFileManagerError(ex: FileManagerException): ResponseEntity<Map<String, String>> {
        val status = when (ex.code) {
            "NOT_FOUND", "ROOT_MISSING" -> HttpStatus.NOT_FOUND
            "ACCESS_DENIED", "NOT_READABLE", "NOT_WRITABLE", "PROTECTED" -> HttpStatus.FORBIDDEN
            "TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(mapOf("error" to ex.code, "message" to ex.message))
    }
}
