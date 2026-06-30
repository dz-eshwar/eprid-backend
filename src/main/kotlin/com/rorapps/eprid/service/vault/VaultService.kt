package com.rorapps.eprid.service.vault

import com.rorapps.eprid.dto.vault.VaultConsentStatus
import com.rorapps.eprid.dto.vault.VaultDocTypeInfo
import com.rorapps.eprid.dto.vault.VaultDocumentResponse
import com.rorapps.eprid.dto.vault.VaultUploadRequest
import com.rorapps.eprid.entity.User
import com.rorapps.eprid.entity.UserRole
import com.rorapps.eprid.entity.VaultDocument
import com.rorapps.eprid.repository.RecyclerRepository
import com.rorapps.eprid.repository.VaultDocumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Service
class VaultService(
    private val vaultRepository: VaultDocumentRepository,
    private val recyclerRepository: RecyclerRepository,
    private val s3Storage: S3VaultStorageService,
) {
    private val ALLOWED_TYPES = setOf(
        "image/jpeg", "image/png", "image/webp", "image/tiff",
        "application/pdf"
    )

    @Transactional
    fun upload(request: VaultUploadRequest, file: MultipartFile, uploadedBy: User): VaultDocumentResponse {
        require(file.contentType in ALLOWED_TYPES) {
            "Unsupported file type '${file.contentType}'. Allowed: JPEG, PNG, WebP, TIFF, PDF"
        }
        require(file.size <= 20 * 1024 * 1024) { "File too large — maximum 20 MB" }

        val recycler = if (uploadedBy.role == UserRole.RECYCLER) {
            recyclerRepository.findByUserId(uploadedBy.id!!)
                ?: throw NoSuchElementException("No recycler profile linked to your account. Please contact support.")
        } else {
            val rid = request.recyclerId
                ?: throw IllegalArgumentException("recyclerId is required for non-RECYCLER users")
            recyclerRepository.findById(rid)
                .orElseThrow { NoSuchElementException("Recycler not found: $rid") }
        }

        val s3Key = s3Storage.store(recycler.id!!, file)

        val doc = vaultRepository.save(
            VaultDocument(
                recycler = recycler,
                uploadedBy = uploadedBy,
                docType = request.docType,
                displayName = request.displayName,
                fileName = file.originalFilename ?: file.name,
                contentType = file.contentType ?: "application/octet-stream",
                fileSizeBytes = file.size,
                s3Key = s3Key,
                consentAcceptedAt = request.consentAcceptedAt,
                notes = request.notes
            )
        )

        return doc.toResponse()
    }

    @Transactional(readOnly = true)
    fun listForRecycler(recyclerId: String, requestedBy: User): List<VaultDocumentResponse> =
        vaultRepository.findActiveByRecyclerId(recyclerId).map { it.toResponse() }

    @Transactional(readOnly = true)
    fun listForUser(userId: String): List<VaultDocumentResponse> =
        vaultRepository.findActiveByUserId(userId).map { it.toResponse() }

    @Transactional
    fun delete(docId: String, requestedBy: User): Boolean {
        val doc = vaultRepository.findById(docId)
            .orElseThrow { NoSuchElementException("Document not found: $docId") }
        if (doc.uploadedBy.id != requestedBy.id) throw SecurityException("Access denied")
        vaultRepository.save(doc.copy(deletedAt = Instant.now()))
        return true
    }

    fun download(docId: String, requestedBy: User): Pair<VaultDocument, ByteArray> {
        val doc = vaultRepository.findById(docId)
            .orElseThrow { NoSuchElementException("Document not found: $docId") }
        if (doc.uploadedBy.id != requestedBy.id) throw SecurityException("Access denied")
        if (doc.deletedAt != null) throw NoSuchElementException("Document has been deleted")
        return doc to s3Storage.download(doc.s3Key)
    }

    fun getConsentStatus(recyclerId: String, userId: String): VaultConsentStatus {
        val docs = vaultRepository.findActiveByRecyclerId(recyclerId)
            .filter { it.uploadedBy.id == userId }
        val earliest = docs.minByOrNull { it.consentAcceptedAt }
        return VaultConsentStatus(
            recyclerId = recyclerId,
            hasAccepted = earliest != null,
            acceptedAt = earliest?.consentAcceptedAt
        )
    }

    fun listDocTypes(): List<VaultDocTypeInfo> = listOf(
        VaultDocTypeInfo("REGISTRATION_CERT",           "Registration Certificate",       "BWMR recycler registration certificate from CPCB"),
        VaultDocTypeInfo("GST_CERT",                    "GST Certificate",                "GST registration certificate"),
        VaultDocTypeInfo("PAN_CARD",                    "PAN Card",                       "Permanent Account Number card"),
        VaultDocTypeInfo("INCORPORATION_CERT",          "Incorporation Certificate",       "Company incorporation or partnership deed"),
        VaultDocTypeInfo("PCB_AUTHORIZATION",           "PCB Authorization",              "State/Central Pollution Control Board consent to operate"),
        VaultDocTypeInfo("HAZARDOUS_WASTE_AUTHORIZATION","Hazardous Waste Authorization", "Authorization under Hazardous Waste Management Rules 2016"),
        VaultDocTypeInfo("EPR_REGISTRATION_CERT",       "EPR Registration Certificate",   "Extended Producer Responsibility registration from CPCB portal"),
        VaultDocTypeInfo("CAPACITY_CERTIFICATE",        "Capacity Certificate",           "Annual recycling capacity (tonnes/yr) assessed by competent authority"),
        VaultDocTypeInfo("PROCESSING_RECEIPT",          "Processing Receipt",             "Recycling completion receipt per batch"),
        VaultDocTypeInfo("WEIGHBRIDGE_SLIP",            "Weighbridge Slip",               "Weight slip proving quantity of batteries received or processed"),
        VaultDocTypeInfo("GATE_PASS",                   "Gate Pass",                      "Inward receipt issued when batteries arrive at facility"),
        VaultDocTypeInfo("CONSIGNMENT_NOTE",            "Consignment Note",               "E-way bill or transport document for battery shipment"),
        VaultDocTypeInfo("CERTIFICATE_OF_RECYCLING",    "Certificate of Recycling",       "Certificate issued to producer confirming batteries were recycled"),
        VaultDocTypeInfo("ANNUAL_RETURN",               "Annual Return",                  "Filed annual compliance return to CPCB/SPCB"),
        VaultDocTypeInfo("QUARTERLY_REPORT",            "Quarterly Report",               "Quarterly progress report submitted to CPCB or SPCB"),
        VaultDocTypeInfo("THIRD_PARTY_AUDIT_REPORT",    "Third-Party Audit Report",       "Independent audit report verifying recycling operations"),
        VaultDocTypeInfo("OTHER",                       "Other Document",                 "Any other relevant compliance or operational document")
    )

    private fun VaultDocument.toResponse() = VaultDocumentResponse(
        id = id!!,
        recyclerId = recycler.id!!,
        recyclerName = recycler.name,
        docType = docType,
        displayName = displayName,
        fileName = fileName,
        contentType = contentType,
        fileSizeBytes = fileSizeBytes,
        notes = notes,
        consentAcceptedAt = consentAcceptedAt,
        uploadedAt = uploadedAt
    )
}
