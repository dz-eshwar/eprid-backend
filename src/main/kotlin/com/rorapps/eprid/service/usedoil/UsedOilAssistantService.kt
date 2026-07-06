package com.rorapps.eprid.service.usedoil

import com.rorapps.eprid.constants.UsedOilFeeTiers
import com.rorapps.eprid.constants.UsedOilTier
import com.rorapps.eprid.dto.usedoil.*
import org.springframework.stereotype.Service
import java.math.RoundingMode

/**
 * Module E — used-oil CA-1/CA-2 registration assistant. Stateless rules engine + static
 * regulatory content, no persistence, no dependency on any battery/tyre entity or constant —
 * matches Module B's (ComplianceCalculatorService) pattern but is a fully independent module.
 *
 * Content grounded in CPCB's "Guidance Document for Registration of Collection Agent-1/2"
 * (read July 2026). Fee tables and the 75km/150km CA-1 service-radius rule should be
 * re-verified against any future CPCB amendment (PRD §7.6 open item).
 */
@Service
class UsedOilAssistantService {

    companion object {
        const val DISCLAIMER =
            "This tool provides informational guidance only — it is not a substitute for CPCB's official " +
            "registration process, SPCB verification, or professional legal/compliance advice. E-PRid does not " +
            "submit anything to any government portal on your behalf."

        const val PAYMENT_NOT_FINAL_STEP_REMINDER =
            "Paying the registration fee on the CPCB portal is not the final step — you must still contact " +
            "your State Pollution Control Board (SPCB) for verification, per CPCB's own guidance."

        const val FALSE_INFO_WARNING =
            "CPCB's guidance states that false or irrelevant information leads to rejection with forfeited fees " +
            "(a fresh application and fee is then required). Post-registration false-information findings can mean " +
            "revocation for up to five years, plus Environmental Compensation charges."

        const val CA1_SERVICE_RADIUS_NOTE =
            "CA-1 collectors typically operate within a 75km pickup radius (up to 150km in specific cases per " +
            "CPCB guidance) — confirm current radius rules before committing to a service area."
    }

    fun determineTier(request: TierDeterminationRequest): TierDeterminationResponse {
        val tier = if (request.hasStorageFacility && request.hasTruckFleet) UsedOilTier.CA_2 else UsedOilTier.CA_1
        val rationale = if (tier == UsedOilTier.CA_2)
            "You reported a storage facility and a truck fleet — this fits the CA-2 (storage + transport) profile."
        else
            "You reported pickup/transport only, without a storage facility and truck fleet — this fits the CA-1 " +
            "(pickup/transport only) profile."
        return TierDeterminationResponse(tier, rationale)
    }

    fun checkCa1Prerequisite(request: Ca1PrerequisiteCheckRequest): Ca1PrerequisiteCheckResponse {
        val requiredContents = listOf(
            "Identity of both parties (your business and the CA-2/recycler)",
            "Scope of the collection arrangement (what waste oil quantities/routes are covered)",
            "Validity period of the agreement",
            "Signatures of authorized persons from both parties"
        )
        return if (request.hasSignedAgreementWithCa2OrRecycler) {
            Ca1PrerequisiteCheckResponse(
                canProceed = true,
                message = "You have a signed agreement in place — you can proceed to the CA-1 application.",
                requiredAgreementContents = requiredContents
            )
        } else {
            Ca1PrerequisiteCheckResponse(
                canProceed = false,
                message = "CA-1 registration requires a signed agreement with a CA-2 or recycler ALREADY in place " +
                    "before applying — the CPCB portal will not accept a CA-1 application without it. Prepare this " +
                    "agreement first; it must contain the items listed below.",
                requiredAgreementContents = requiredContents
            )
        }
    }

    fun getCa2ReadinessChecklist(): Ca2ReadinessChecklistResponse = Ca2ReadinessChecklistResponse(
        items = listOf(
            ReadinessChecklistItem("Storage facility", "Sited away from residential areas, per CPCB siting rules."),
            ReadinessChecklistItem("Truck fleet", "Vehicles registered and available for waste-oil transport."),
            ReadinessChecklistItem("Geo-tagged site photographs", "Required as part of the CA-2 application's Geo-Images section."),
            ReadinessChecklistItem("Lab facility access", "Own lab or a documented arrangement with a third-party lab."),
            ReadinessChecklistItem("GST-linked account", "Business entity registered and GST-linked for the application."),
            ReadinessChecklistItem("Attached CA-1s/recyclers listed", "Every CA-1 or recycler you work with must be listed on the portal."),
            ReadinessChecklistItem("Storage capacity and equipment details", "Documented for the Storage Capacity and Equipment section.")
        )
    )

    fun calculateFee(request: FeeCalculationRequest): FeeCalculationResponse {
        val registrationFee = UsedOilFeeTiers.registrationFee(request.avgAnnualQuantityMt)
        val processingCharge = registrationFee.multiply(UsedOilFeeTiers.ANNUAL_PROCESSING_CHARGE_PCT)
            .setScale(2, RoundingMode.HALF_UP)
        return FeeCalculationResponse(
            registrationFeeRs = registrationFee,
            annualProcessingChargeRs = processingCharge,
            totalFirstYearRs = registrationFee.add(processingCharge),
            tierLabel = UsedOilFeeTiers.tierLabel(registrationFee)
        )
    }

    fun getCa1FormChecklist(): Ca1FormChecklistResponse = Ca1FormChecklistResponse(
        sections = listOf("Authorized Person details", "Vehicle details", "Oil Collection Details"),
        agreementUploadNote = "Upload your signed CA-2/recycler agreement as a PDF (max 2MB) with the application."
    )

    fun buildSummary(request: UsedOilSummaryRequest): UsedOilSummaryResponse {
        val fee = calculateFee(FeeCalculationRequest(request.avgAnnualQuantityMt))

        val met = mutableListOf<String>()
        val outstanding = mutableListOf<String>()
        val nextStep: String

        if (request.tier == UsedOilTier.CA_1) {
            when (request.ca1PrerequisiteMet) {
                true -> {
                    met += "Signed agreement with a CA-2/recycler is in place"
                    nextStep = "Proceed to the CA-1 application: Authorized Person details, Vehicle details, " +
                        "Oil Collection Details. $PAYMENT_NOT_FINAL_STEP_REMINDER"
                }
                false -> {
                    outstanding += "Signed agreement with a CA-2/recycler (required before applying)"
                    nextStep = "Prepare a signed agreement with a CA-2 or recycler before starting the CA-1 " +
                        "application — the portal will not accept it without one."
                }
                null -> {
                    outstanding += "Confirm whether a signed agreement with a CA-2/recycler is in place"
                    nextStep = "Confirm your prerequisite agreement status before proceeding."
                }
            }
        } else {
            outstanding += "Confirm all CA-2 readiness items (storage facility, fleet, geo-tagged photos, " +
                "lab access, GST-linked account, attached CA-1/recycler list) against the checklist"
            nextStep = "Work through the CA-2 readiness checklist, then begin the multi-tab CPCB application " +
                "(General details, Collection Facility details, Geo-Images, Lab Facilities, Storage Capacity and " +
                "Equipment, Transportation facilities, Payment). $PAYMENT_NOT_FINAL_STEP_REMINDER"
        }

        return UsedOilSummaryResponse(
            tier = request.tier,
            feeBreakdown = fee,
            prerequisitesMet = met,
            prerequisitesOutstanding = outstanding,
            nextStep = "$nextStep $FALSE_INFO_WARNING",
            disclaimer = DISCLAIMER
        )
    }
}
