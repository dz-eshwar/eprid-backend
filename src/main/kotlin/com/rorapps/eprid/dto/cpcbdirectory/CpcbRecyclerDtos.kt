package com.rorapps.eprid.dto.cpcbdirectory

import com.rorapps.eprid.entity.RiskRating
import com.rorapps.eprid.entity.ScoreConfidence
import java.math.BigDecimal
import java.time.LocalDate

/** Customer-facing search result. Deliberately excludes authorizedName/Email/Mobile (PII of an
 *  individual, not the company) — those stay internal/ops-only, see product_document_built_state.md. */
data class CpcbRecyclerSearchResult(
    val id: String,
    val cpcbId: String?,
    val recyclerName: String,
    val recyclerAddress: String?,
    val stateId: String?,
    /** Resolved via cpcb_state_codes — null when state_id is unmapped (see V17), not when it's blank. */
    val stateName: String?,
    val recyclerGstNo: String?,
    val consentAirExpiry: LocalDate?,
    val consentWaterExpiry: LocalDate?,
    val hwmdValidExpiry: LocalDate?,
    val dicValidExpiry: LocalDate?,
    val recyclingCapacity: BigDecimal?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val authorizations: List<CpcbAuthorizationDto>,
    val dataQualityPartialCapture: Boolean,
    val dataQualityNotes: String?,
    val latestScore: CpcbRecyclerScoreDto?
)

data class CpcbStateDto(
    val stateId: String,
    val stateName: String
)

data class CpcbAuthorizationDto(
    val categoryCode: String,
    val categoryLabel: String
)

data class CpcbRecyclerScoreDto(
    val compositeScore: Int,
    val riskBand: RiskRating,
    val flags: List<String>,
    val unassessed: List<String>,
    val layerBreakdown: Map<String, Any?>,
    val scoreConfidence: ScoreConfidence,
    val scoredAt: String
)

data class CpcbIngestionSummaryDto(
    val rowsRead: Int,
    val rowsUpserted: Int,
    val rowsFlaggedPartialCapture: Int,
    val rowsMissingSourceId: Int,
    val errors: List<String>
)
