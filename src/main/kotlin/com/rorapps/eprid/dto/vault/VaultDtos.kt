package com.rorapps.eprid.dto.vault

import com.rorapps.eprid.entity.VaultDocType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class VaultUploadRequest(
    @field:NotNull val docType: VaultDocType,
    @field:NotBlank val displayName: String,
    val notes: String? = null,
    /** Recycler ID — optional for RECYCLER-role users (derived from their account); required for other roles */
    val recyclerId: String? = null,
    /** ISO-8601 timestamp when the recycler accepted the consent disclosure */
    @field:NotNull val consentAcceptedAt: Instant
)

data class VaultDocumentResponse(
    val id: String,
    val recyclerId: String,
    val recyclerName: String,
    val docType: VaultDocType,
    val displayName: String,
    val fileName: String,
    val contentType: String,
    val fileSizeBytes: Long,
    val notes: String?,
    val consentAcceptedAt: Instant,
    val uploadedAt: Instant
)

data class VaultConsentStatus(
    val recyclerId: String,
    val hasAccepted: Boolean,
    val acceptedAt: Instant?
)

data class VaultDocTypeInfo(
    val type: String,
    val label: String,
    val description: String
)
