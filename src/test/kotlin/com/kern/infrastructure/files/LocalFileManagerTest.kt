package com.kern.infrastructure.files

import com.kern.config.FileManagerProperties
import com.kern.domain.files.FileEntryType
import com.kern.domain.files.FileManagerException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalFileManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var fileManager: LocalFileManager

    @BeforeEach
    fun setUp() {
        val properties = FileManagerProperties(
            allowedRoots = listOf(tempDir.toString()),
            maxContentBytes = 1024,
            maxUploadBytes = 1024 * 1024,
        )
        val guard = FilePathGuard(properties)
        fileManager = LocalFileManager(properties, guard)
    }

    @Test
    fun `lists directory entries`() {
        Files.writeString(tempDir.resolve("readme.txt"), "hello")
        Files.createDirectories(tempDir.resolve("nested"))

        val listing = fileManager.listDirectory(tempDir.toString())

        assertEquals(fileManager.defaultDirectory(), listing.path)
        assertTrue(listing.entries.any { it.name == "readme.txt" && it.type == FileEntryType.FILE })
        assertTrue(listing.entries.any { it.name == "nested" && it.type == FileEntryType.DIRECTORY })
    }

    @Test
    fun `reads text file content`() {
        val file = tempDir.resolve("note.txt")
        Files.writeString(file, "line one")

        val content = fileManager.readContent(file.toString())

        assertEquals("line one", content.content)
        assertEquals(false, content.truncated)
    }

    @Test
    fun `rejects path outside allowed roots`() {
        assertFailsWith<FileManagerException> {
            fileManager.listDirectory("/etc")
        }
    }

    @Test
    fun `uploads and deletes file`() {
        val bytes = "uploaded".toByteArray()
        val result = fileManager.upload(
            tempDir.toString(),
            "new.txt",
            bytes.inputStream(),
            bytes.size.toLong(),
        )

        assertTrue(result.success)
        val target = tempDir.resolve("new.txt")
        assertTrue(Files.exists(target))

        val deleteResult = fileManager.delete(listOf(target.toString()))
        assertTrue(deleteResult.success)
        assertTrue(!Files.exists(target))
    }
}
