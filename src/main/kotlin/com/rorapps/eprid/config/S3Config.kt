package com.rorapps.eprid.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class S3Config(
    @Value("\${app.aws.region}") private val region: String,
    @Value("\${app.aws.access-key}") private val accessKey: String,
    @Value("\${app.aws.secret-key}") private val secretKey: String,
) {
    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(
            if (accessKey.isNotBlank())
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            else
                DefaultCredentialsProvider.create() // falls back to IAM role / env vars on EC2/ECS
        )
        .build()
}
