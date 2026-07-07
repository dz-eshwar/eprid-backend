package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.TyreEprReconciliation
import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.dto.plausibility.PlausibilitySubCheck
import com.rorapps.eprid.entity.SubCheckStatus
import com.rorapps.eprid.entity.VerificationCheck
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Tyre/TPO plausibility logic (Module D). Slot 2 (capacity ceiling) and slot 3 (absolute batch
 * size) reuse the generic checks unchanged — only slot 1 is tyre-specific, per the PRD's
 * "what's new" scope for this module.
 *
 * Slot 1 reconciles the recycler's claimed EPR certificate credit against CPCB's own
 * certificate-generation formula (QEPR = QP × CF × WP, §7.5) — replacing the earlier
 * TPO-yield-ratio benchmark, which the PRD explicitly superseded as an unverified estimate.
 */
@Component
class TyrePlausibilityStrategy : PlausibilityStrategy {

    override fun supports(wasteStream: WasteStreamType) = wasteStream == WasteStreamType.TYRE

    override fun runChecks(check: VerificationCheck): List<PlausibilitySubCheck> {
        val reconciliation = checkEprCreditReconciliation(check)
        val capacity        = checkCapacityCeiling(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)
        val batchSize        = checkAbsoluteBatchSize(check.batchWeightTonnes, check.recycler.selfReportedCapacityT)
        return listOf(reconciliation, capacity, batchSize)
    }

    // ─── Sub-check: EPR credit reconciliation (QEPR = QP × CF × WP) ───────────

    private fun checkEprCreditReconciliation(check: VerificationCheck): PlausibilitySubCheck {
        val qp = check.claimedOutputQuantity
        val endProduct = check.tyreEndProduct
        val claimedCredit = check.claimedEprCreditKg

        if (qp == null || endProduct == null || claimedCredit == null) {
            val missing = listOfNotNull(
                if (qp == null) "end-product quantity sold" else null,
                if (endProduct == null) "end-product type" else null,
                if (claimedCredit == null) "claimed EPR certificate credit" else null
            ).joinToString(", ")
            return PlausibilitySubCheck(
                name = "EPR credit reconciliation (QEPR = QP × CF × WP)",
                status = SubCheckStatus.UNVERIFIABLE,
                detail = "Cannot reconcile claimed EPR credit against CPCB's certificate-generation formula — missing: $missing.",
                referenceValue = "Formula: QEPR = QP × CF × WP (CPCB tyre EPR certificate guidance, §7.5)"
            )
        }

        val wp = if (check.tyreImported) TyreEprReconciliation.IMPORTED_TYRE_WEIGHTAGE else endProduct.weightage
        val computedQepr = qp.multiply(endProduct.conversionFactor).multiply(wp)
            .setScale(3, RoundingMode.HALF_UP)

        val deviationPct = claimedCredit.subtract(computedQepr).abs()
            .divide(computedQepr, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))

        val referenceValue = "Computed QEPR: $computedQepr kg (QP=$qp × CF=${endProduct.conversionFactor} × WP=$wp); claimed: $claimedCredit kg"

        return when {
            deviationPct > TyreEprReconciliation.WARN_TOLERANCE_PCT -> PlausibilitySubCheck(
                name = "EPR credit reconciliation (QEPR = QP × CF × WP)",
                status = SubCheckStatus.FAIL,
                detail = "Claimed EPR credit of $claimedCredit kg deviates ${deviationPct.setScale(1, RoundingMode.HALF_UP)}% " +
                         "from CPCB's formula-computed value of $computedQepr kg — well outside a plausible margin.",
                referenceValue = referenceValue
            )
            deviationPct > TyreEprReconciliation.PASS_TOLERANCE_PCT -> PlausibilitySubCheck(
                name = "EPR credit reconciliation (QEPR = QP × CF × WP)",
                status = SubCheckStatus.WARN,
                detail = "Claimed EPR credit of $claimedCredit kg deviates ${deviationPct.setScale(1, RoundingMode.HALF_UP)}% " +
                         "from CPCB's formula-computed value of $computedQepr kg — outside the default tolerance; verify.",
                referenceValue = referenceValue
            )
            else -> PlausibilitySubCheck(
                name = "EPR credit reconciliation (QEPR = QP × CF × WP)",
                status = SubCheckStatus.PASS,
                detail = "Claimed EPR credit of $claimedCredit kg is within tolerance of CPCB's formula-computed value of $computedQepr kg.",
                referenceValue = referenceValue
            )
        }
    }
}
