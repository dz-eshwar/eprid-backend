package com.rorapps.eprid.service.vault

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.ServerSideEncryption
import java.util.UUID

@Service
class S3VaultStorageService(
    private val s3Client: S3Client,
    @Value("\${app.aws.s3-bucket}") private val bucket: String,
) {
    fun store(recyclerId: String, file: MultipartFile): String {
        val ext = file.originalFilename
            ?.substringAfterLast('.', "")
            ?.let { if (it.isNotEmpty()) ".$it" else "" }
            ?: ""
        val key = "vault/$recyclerId/${UUID.randomUUID()}$ext"
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.contentType ?: "application/octet-stream")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build(),
            RequestBody.fromBytes(file.bytes)
        )
        return key
    }

    fun download(s3Key: String): ByteArray =
        s3Client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(s3Key).build()
        ).asByteArray()

    fun delete(s3Key: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(bucket).key(s3Key).build()
        )
    }
}
