package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.dto.vault.VaultConsentStatus
import com.rorapps.eprid.dto.vault.VaultDocTypeInfo
import com.rorapps.eprid.dto.vault.VaultDocumentResponse
import com.rorapps.eprid.dto.vault.VaultUploadRequest
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.entity.VaultDocType
import com.rorapps.eprid.service.vault.VaultService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@RestController
@RequestMapping("/api/v1/vault")
@Tag(name = "Recycler Document Vault", description = "Module C1 — standalone document storage for recyclers")
@SecurityRequirement(name = "Bearer Authentication")
class VaultController(private val vaultService: VaultService) {

    @GetMapping("/document-types")
    @Operation(summary = "List all supported vault document types with labels and descriptions")
    fun documentTypes(): ResponseEntity<ApiResponse<List<VaultDocTypeInfo>>> =
        ResponseEntity.ok(ApiResponse.ok(vaultService.listDocTypes()))

    @GetMapping("/consent/{recyclerId}")
    @Operation(summary = "Check whether this user has accepted the vault consent disclosure for a recycler")
    fun consentStatus(
        @PathVariable recyclerId: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<VaultConsentStatus>> =
        ResponseEntity.ok(ApiResponse.ok(vaultService.getConsentStatus(recyclerId, currentUser.id!!)))

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload a document to the vault",
        description = """
            Requires explicit consent: supply consentAcceptedAt (ISO-8601) — this is the timestamp
            the recycler viewed and accepted the vault disclosure copy. Stored as an immutable audit
            trail event. Allowed file types: JPEG, PNG, WebP, TIFF, PDF. Max 20 MB.
            Doc types: REGISTRATION_CERT | GST_CERT | PROCESSING_RECEIPT | OTHER
        """
    )
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("recyclerId", required = false) recyclerId: String?,
        @RequestParam("docType") docType: String,
        @RequestParam("displayName") displayName: String,
        @RequestParam("consentAcceptedAt") consentAcceptedAt: String,
        @RequestParam("notes", required = false) notes: String?,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<VaultDocumentResponse>> {
        val request = VaultUploadRequest(
            docType = VaultDocType.valueOf(docType.uppercase()),
            displayName = displayName,
            notes = notes,
            recyclerId = recyclerId,
            consentAcceptedAt = Instant.parse(consentAcceptedAt)
        )
        return ResponseEntity.ok(ApiResponse.ok(vaultService.upload(request, file, currentUser)))
    }

    @GetMapping("/recycler/{recyclerId}")
    @Operation(summary = "List all active vault documents for a recycler")
    fun listForRecycler(
        @PathVariable recyclerId: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<List<VaultDocumentResponse>>> =
        ResponseEntity.ok(ApiResponse.ok(vaultService.listForRecycler(recyclerId, currentUser)))

    @GetMapping
    @Operation(summary = "List all vault documents uploaded by the current user")
    fun listMine(
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<List<VaultDocumentResponse>>> =
        ResponseEntity.ok(ApiResponse.ok(vaultService.listForUser(currentUser.id!!)))

    @GetMapping("/{docId}/download")
    @Operation(summary = "Download a vault document")
    fun download(
        @PathVariable docId: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ByteArray> {
        val (doc, bytes) = vaultService.download(docId, currentUser)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${doc.fileName}\"")
            .contentType(MediaType.parseMediaType(doc.contentType))
            .body(bytes)
    }

    @DeleteMapping("/{docId}")
    @Operation(summary = "Soft-delete a vault document")
    fun delete(
        @PathVariable docId: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<ApiResponse<Boolean>> =
        ResponseEntity.ok(ApiResponse.ok(vaultService.delete(docId, currentUser)))
}
