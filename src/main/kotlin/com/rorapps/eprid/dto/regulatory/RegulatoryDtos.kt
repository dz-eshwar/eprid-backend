package com.rorapps.eprid.dto.regulatory

import com.rorapps.eprid.entity.RegulatoryStatus
import java.time.LocalDate

data class RegulatoryFindingDto(
    val id: String,
    val source: String,
    val findingType: String,
    val severity: String,
    val title: String,
    val summary: String,
    val url: String?,
    val findingDate: LocalDate?,
    val confidence: String
)

data class RegulatoryHistoryResponse(
    val checkId: String,
    val recyclerName: String,
    val bwmrRegNumber: String?,
    val status: RegulatoryStatus,
    val overallRisk: String?,        // LOW | MEDIUM | HIGH | UNKNOWN
    val rationale: String?,
    val recommendation: String?,
    val caveat: String?,
    val findings: List<RegulatoryFindingDto>
)

/** Internal DTO for Claude API JSON response — not serialised to clients */
data class ClaudeRegulatoryAnalysis(
    val overallRisk: String,
    val rationale: String,
    val findings: List<ClaudeFinding>,
    val recommendation: String,
    val caveat: String
)

data class ClaudeFinding(
    val source: String,
    val findingType: String,
    val severity: String,
    val title: String,
    val summary: String,
    val confidence: String
)
