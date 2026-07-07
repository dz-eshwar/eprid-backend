package com.rorapps.eprid.dto.check

import com.rorapps.eprid.constants.TyreEndProduct
import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.dto.plausibility.PlausibilityCheckResponse
import com.rorapps.eprid.entity.CheckStatus
import com.rorapps.eprid.entity.RegulatoryStatus
import com.rorapps.eprid.entity.RiskRating
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class CreateCheckRequest(
    // Recycler details (upserted by BWMR reg number if provided)
    @field:NotBlank
    val recyclerName: String,
    val bwmrRegNumber: String? = null,
    val recyclerState: String? = null,
    val recyclerSelfReportedCapacityT: BigDecimal? = null,

    // Producer details (upserted by CPCB reg number if provided)
    @field:NotBlank
    val producerName: String,
    val cpcbRegNumber: String? = null,

    /** Defaults to BATTERY for backward compatibility with existing callers. */
    val wasteStream: WasteStreamType = WasteStreamType.BATTERY,

    // Batch details claimed by the recycler
    @field:NotNull
    @field:DecimalMin("0.001")
    val batchWeightTonnes: BigDecimal,

    @field:NotNull
    @field:DecimalMin("0.0")
    @field:DecimalMax("100.0")
    val claimedRecoveryPct: BigDecimal,

    @field:NotNull
    val processingDate: LocalDate,

    /** Tyre only: quantity of end-product sold (QP). Unit depends on [tyreEndProduct]. Ignored for other waste streams. */
    val claimedOutputQuantity: BigDecimal? = null,

    /** Tyre only: which end-product category was sold — selects CF/WP from CPCB's table (§7.5). */
    val tyreEndProduct: TyreEndProduct? = null,

    /** Tyre only: true if the underlying waste tyre was imported — forces WP = 1.0. */
    val tyreImported: Boolean = false,

    /** Tyre only: the recycler's claimed EPR certificate credit (kg) for this batch. */
    val claimedEprCreditKg: BigDecimal? = null,

    /** Optional link back to a calculator session that prompted this check */
    val complianceEstimateId: String? = null
)

data class VerificationCheckResponse(
    val id: String,
    val recyclerName: String,
    val recyclerId: String,
    val producerName: String,
    val producerId: String,
    val wasteStream: WasteStreamType,
    val batchWeightTonnes: BigDecimal,
    val claimedRecoveryPct: BigDecimal,
    val claimedOutputQuantity: BigDecimal?,
    val tyreEndProduct: TyreEndProduct?,
    val tyreImported: Boolean,
    val claimedEprCreditKg: BigDecimal?,
    val processingDate: LocalDate,
    val status: CheckStatus,
    val riskRating: RiskRating?,
    val riskSummary: String?,
    val evidenceCount: Int,
    val complianceEstimateId: String?,
    // Populated immediately on check creation
    val plausibility: PlausibilityCheckResponse? = null,
    // Populated async by Agent 5
    val regulatoryStatus: RegulatoryStatus = RegulatoryStatus.NOT_STARTED,
    val regulatoryRisk: String? = null,
    val regulatorySummary: String? = null
)
