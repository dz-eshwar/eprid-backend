package com.rorapps.eprid.service.forensics

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

@Service
class FileStorageService(
    @Value("\${app.storage.base-path:./uploads}") private val basePath: String
) {
    private val root: Path by lazy {
        Paths.get(basePath).also { Files.createDirectories(it) }
    }

    fun store(checkId: String, file: MultipartFile): Path {
        val dir = root.resolve(checkId).also { Files.createDirectories(it) }
        val ext = file.originalFilename?.substringAfterLast('.', "") ?: ""
        val filename = "${UUID.randomUUID()}${if (ext.isNotEmpty()) ".$ext" else ""}"
        val target = dir.resolve(filename)
        file.inputStream.use { Files.copy(it, target) }
        return target
    }

    fun load(storagePath: String): ByteArray = Files.readAllBytes(Paths.get(storagePath))
}
