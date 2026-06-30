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
