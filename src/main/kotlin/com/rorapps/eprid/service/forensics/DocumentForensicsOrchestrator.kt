package com.rorapps.eprid.service.forensics

import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.dto.forensics.EvidenceUploadResponse
import com.rorapps.eprid.dto.forensics.FileForensicsResult
import com.rorapps.eprid.dto.forensics.ForensicsCheckResult
import com.rorapps.eprid.dto.forensics.ForensicsCheckStatus
import com.rorapps.eprid.entity.Evidence
import com.rorapps.eprid.entity.ForensicsStatus
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.EvidenceRepository
import com.rorapps.eprid.repository.VerificationCheckRepository
import com.rorapps.eprid.service.CompositeScoringService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path

@Service
class DocumentForensicsOrchestrator(
    private val fileStorageService: FileStorageService,
    private val exifService: ExifForensicsService,
    private val pdfService: PdfForensicsService,
    private val hashService: ImageHashService,
    private val evidenceRepository: EvidenceRepository,
    private val checkRepository: VerificationCheckRepository,
    private val compositeScoringService: CompositeScoringService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/tiff")
    private val PDF_TYPE = "application/pdf"
    private val ALLOWED_TYPES = IMAGE_TYPES + PDF_TYPE

    /**
     * @param files         uploaded files
     * @param evidenceTypes one EvidenceType per file, aligned by index — defaults to OTHER if list is shorter
     */
    @Transactional
    fun processUploads(
        check: VerificationCheck,
        files: List<MultipartFile>,
        evidenceTypes: List<EvidenceType>
    ): EvidenceUploadResponse {
        require(files.isNotEmpty()) { "At least one file is required" }
        require(files.size <= 10) { "Maximum 10 files per upload" }

        val recyclerState = check.recycler.state  // may be null — handled in ExifForensicsService

        val results = files.mapIndexed { index, file ->
            val evidenceType = evidenceTypes.getOrElse(index) { EvidenceType.OTHER }
            require(file.contentType in ALLOWED_TYPES) {
                "Unsupported file type '${file.contentType}' for '${file.originalFilename}'. Allowed: JPEG, PNG, WebP, TIFF, PDF"
            }
            processFile(check, file, evidenceType, recyclerState)
        }

        // Document forensics is one of composite scoring's five signals (§7.1a) — recompute
        // now that this upload's results are persisted
        compositeScoringService.recomputeAndSave(check)

        return EvidenceUploadResponse(
            checkId = check.id!!,
            filesProcessed = results.size,
            results = results
        )
    }

    private fun processFile(
        check: VerificationCheck,
        file: MultipartFile,
        evidenceType: EvidenceType,
        recyclerState: String?
    ): FileForensicsResult {
        val storagePath: Path = fileStorageService.store(check.id!!, file)
        val tempFile = storagePath.toFile()
        val contentType = file.contentType ?: "application/octet-stream"
        val allChecks = mutableListOf<ForensicsCheckResult>()

        var lat: Double? = null
        var lon: Double? = null
        var exifDatetime: java.time.Instant? = null
        var exifDevice: String? = null
        var resolvedState: String? = null
        var stateMatch: StateMatchStatus? = null
        var pdfAuthor: String? = null
        var pdfCreator: String? = null
        var pdfCreatedAt: java.time.Instant? = null
        var pdfModifiedAt: java.time.Instant? = null
        var phash: String? = null

        when {
            contentType in IMAGE_TYPES -> {
                val exif = exifService.analyze(tempFile, check.processingDate, evidenceType, recyclerState)
                allChecks += exif.checks
                lat = exif.latitude
                lon = exif.longitude
                exifDatetime = exif.datetime
                exifDevice = exif.device
                resolvedState = exif.resolvedState
                stateMatch = exif.stateMatch

                phash = hashService.computeDHash(tempFile)
                if (phash != null) {
                    allChecks += hashService.checkForDuplicates(phash, excludeEvidenceId = null)
                } else {
                    allChecks += ForensicsCheckResult(
                        checkName = "Reverse image duplicate check",
                        status = ForensicsCheckStatus.UNVERIFIABLE,
                        detail = "Could not compute image hash — file may be corrupt"
                    )
                }
            }

            contentType == PDF_TYPE -> {
                val pdf = pdfService.analyze(tempFile, check.processingDate, evidenceType)
                allChecks += pdf.checks
                pdfAuthor = pdf.author
                pdfCreator = pdf.creator
                pdfCreatedAt = pdf.createdAt
                pdfModifiedAt = pdf.modifiedAt
            }
        }

        val overallStatus = deriveOverallStatus(allChecks)

        val saved = evidenceRepository.save(
            Evidence(
                check = check,
                fileName = file.originalFilename ?: "upload",
                contentType = contentType,
                fileSizeBytes = file.size,
                storagePath = storagePath.toString(),
                exifLatitude = lat,
                exifLongitude = lon,
                exifDatetime = exifDatetime,
                exifDevice = exifDevice,
                pdfAuthor = pdfAuthor,
                pdfCreator = pdfCreator,
                pdfCreatedAt = pdfCreatedAt,
                pdfModifiedAt = pdfModifiedAt,
                imagePhash = phash,
                evidenceType = evidenceType,
                resolvedState = resolvedState,
                stateMatch = stateMatch,
                forensicsStatus = overallStatus,
                forensicsNotes = allChecks
                    .filter { it.status != ForensicsCheckStatus.PASS }
                    .joinToString("\n") { "[${it.status}] ${it.checkName}: ${it.detail}" }
                    .ifBlank { null }
            )
        )

        return FileForensicsResult(
            evidenceId = saved.id!!,
            fileName = file.originalFilename ?: "upload",
            checks = allChecks,
            overallStatus = overallStatus,
            notes = saved.forensicsNotes
        )
    }

    private fun deriveOverallStatus(checks: List<ForensicsCheckResult>): ForensicsStatus {
        if (checks.isEmpty()) return ForensicsStatus.UNVERIFIABLE
        return when {
            checks.any { it.status == ForensicsCheckStatus.FAIL } -> ForensicsStatus.FAIL
            checks.all { it.status == ForensicsCheckStatus.PASS } -> ForensicsStatus.PASS
            else -> ForensicsStatus.UNVERIFIABLE
        }
    }
}
