package com.rorapps.eprid.dto.usedoil

import com.rorapps.eprid.constants.UsedOilTier
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

// ─── Tier determination ────────────────────────────────────────────────────

data class TierDeterminationRequest(
    val hasStorageFacility: Boolean,
    val hasTruckFleet: Boolean
)

data class TierDeterminationResponse(
    val tier: UsedOilTier,
    val rationale: String
)

// ─── CA-1 prerequisite gate ─────────────────────────────────────────────────

data class Ca1PrerequisiteCheckRequest(
    val hasSignedAgreementWithCa2OrRecycler: Boolean
)

data class Ca1PrerequisiteCheckResponse(
    val canProceed: Boolean,
    val message: String,
    val requiredAgreementContents: List<String>
)

// ─── CA-2 readiness checklist ────────────────────────────────────────────────

data class ReadinessChecklistItem(
    val label: String,
    val description: String
)

data class Ca2ReadinessChecklistResponse(
    val items: List<ReadinessChecklistItem>
)

// ─── Fee calculation ─────────────────────────────────────────────────────────

data class FeeCalculationRequest(
    @field:NotNull
    @field:DecimalMin("0.0")
    val avgAnnualQuantityMt: BigDecimal
)

data class FeeCalculationResponse(
    val registrationFeeRs: BigDecimal,
    val annualProcessingChargeRs: BigDecimal,
    val totalFirstYearRs: BigDecimal,
    val tierLabel: String
)

// ─── CA-1 form checklist ─────────────────────────────────────────────────────

data class Ca1FormChecklistResponse(
    val sections: List<String>,
    val agreementUploadNote: String,
    val maxAgreementFileSizeMb: Int = 2
)

// ─── Application details (autofill for the CA-1/CA-2 application form) ───────

data class Ca1ApplicationDetails(
    val authorizedPersonName: String? = null,
    val authorizedPersonDesignation: String? = null,
    val authorizedPersonMobile: String? = null,
    val authorizedPersonEmail: String? = null,
    val vehicleRegistrationNumber: String? = null,
    val vehicleType: String? = null,
    val vehicleCapacityKl: BigDecimal? = null,
    val collectionAreas: String? = null,
    val estimatedMonthlyCollectionKl: BigDecimal? = null
)

data class Ca2ApplicationDetails(
    val authorizedPersonName: String? = null,
    val authorizedPersonDesignation: String? = null,
    val authorizedPersonMobile: String? = null,
    val authorizedPersonEmail: String? = null,
    val storageFacilityAddress: String? = null,
    val storageCapacityKl: BigDecimal? = null,
    val gstNumber: String? = null,
    val labFacilityDetails: String? = null,
    val attachedCa1sOrRecyclers: String? = null
)

// ─── Summary ─────────────────────────────────────────────────────────────────

data class UsedOilSummaryRequest(
    val tier: UsedOilTier,
    @field:NotNull
    @field:DecimalMin("0.0")
    val avgAnnualQuantityMt: BigDecimal,
    val ca1PrerequisiteMet: Boolean? = null,
    val ca1ApplicationDetails: Ca1ApplicationDetails? = null,
    val ca2ApplicationDetails: Ca2ApplicationDetails? = null
)

data class UsedOilSummaryResponse(
    val tier: UsedOilTier,
    val feeBreakdown: FeeCalculationResponse,
    val prerequisitesMet: List<String>,
    val prerequisitesOutstanding: List<String>,
    val nextStep: String,
    val disclaimer: String
)
