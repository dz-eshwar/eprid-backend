package com.rorapps.eprid.dto.check

import com.rorapps.eprid.constants.BatteryChemistry
import com.rorapps.eprid.constants.BatteryMetal
import com.rorapps.eprid.constants.CompositionCheckResult
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
    val recyclerGstNumber: String? = null,

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

    /** Battery only: chemistry declared for this batch — drives the composition-table check (§1). Ignored for other waste streams. */
    val declaredBatteryChemistry: BatteryChemistry? = null,

    /** Battery only: per-metal claimed recovery weights, checked against [declaredBatteryChemistry]'s
     *  CPCB composition range. Empty list skips the composition check entirely (treated as not submitted). */
    val claimedMetalRecoveries: List<ClaimedMetalRecoveryInput> = emptyList(),

    /** Date the underlying certificate was actually issued — distinct from when this check is run.
     *  Falls back to [processingDate] when not supplied. */
    val certificateDate: LocalDate? = null,

    /** Optional link back to a calculator session that prompted this check */
    val complianceEstimateId: String? = null
)

data class ClaimedMetalRecoveryInput(
    val metal: BatteryMetal,
    @field:NotNull
    @field:DecimalMin("0.0")
    val claimedWeightKg: BigDecimal
)

data class MetalCompositionCheckDto(
    val metal: BatteryMetal,
    val claimedPct: BigDecimal?,
    val expectedMin: BigDecimal,
    val expectedMax: BigDecimal,
    val result: CompositionCheckResult,
    val detail: String
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
    val declaredBatteryChemistry: BatteryChemistry? = null,
    val compositionChecks: List<MetalCompositionCheckDto> = emptyList(),
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
    val regulatorySummary: String? = null,
    // Composite risk scoring (§7.1a) — recomputed as plausibility/evidence/regulatory history
    // each complete, so early in a check's life these sub-scores reflect signals that haven't
    // run yet (neutral default), not a final verdict
    val compositeScore: Int? = null,
    val compositeScoreBreakdown: CompositeScoreBreakdown? = null,
    val hardDisqualified: Boolean = false,
    val hardDisqualificationReason: String? = null
)

data class CompositeScoreBreakdown(
    val registrationSubScore: Int?,
    val capacitySubScore: Int?,
    val invoiceSubScore: Int?,
    val forensicsSubScore: Int?,
    val regulatorySubScore: Int?
)
