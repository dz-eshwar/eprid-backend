package com.rorapps.eprid.dto.forensics

import com.rorapps.eprid.entity.ForensicsStatus

data class FileForensicsResult(
    val evidenceId: String,
    val fileName: String,
    val checks: List<ForensicsCheckResult>,
    val overallStatus: ForensicsStatus,
    val notes: String?
)

data class ForensicsCheckResult(
    val checkName: String,
    val status: ForensicsCheckStatus,
    val detail: String
)

enum class ForensicsCheckStatus { PASS, FAIL, UNVERIFIABLE }

data class EvidenceUploadResponse(
    val checkId: String,
    val filesProcessed: Int,
    val results: List<FileForensicsResult>
)

/**
 * Read-later summary for a single piece of evidence — used when reopening a check's results
 * outside the upload flow. No per-sub-check breakdown is persisted, only the already-joined
 * [notes] string stored on `Evidence.forensicsNotes` at upload time.
 */
data class EvidenceSummaryDto(
    val evidenceId: String,
    val fileName: String,
    val evidenceType: String,
    val overallStatus: ForensicsStatus,
    val notes: String?,
    val uploadedAt: java.time.Instant
)
