package com.rorapps.eprid.service

import com.rorapps.eprid.repository.CpcbRecyclerRepository
import com.rorapps.eprid.repository.RecyclerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

enum class RegistrationValidity { VALID, EXPIRED, UNKNOWN }

/**
 * Point-in-time registration validity for hard-disqualification rule 1
 * (feature_spec_close_scoring_gaps.md §2, rule 1). Reads the CPCB directory's own consent/HWM/DIC
 * expiry dates for the [Recycler]'s linked [CpcbRecycler] row (via [Recycler.cpcbRecyclerId]) as of
 * a given date — not [RecyclerCredentialCheck], which only tracks GST/UDYAM/MCA KYC-verification
 * pass/fail history, not a registration validity window (confirmed: no start/end date fields exist
 * on that entity).
 *
 * SUSPENDED is deliberately not derived here: CpcbRecycler's `inspectionStatus` /
 * `internalAppStatus` / `applicationStatus` are raw integer codes with no confirmed decode table —
 * fabricating a SUSPENDED signal from an unconfirmed code would be worse than not checking it.
 */
@Service
class RegistrationValidityAtDateService(
    private val recyclerRepository: RecyclerRepository,
    private val cpcbRecyclerRepository: CpcbRecyclerRepository
) {

    @Transactional(readOnly = true)
    fun check(recyclerId: String, asOfDate: LocalDate): RegistrationValidity {
        val recycler = recyclerRepository.findById(recyclerId).orElse(null) ?: return RegistrationValidity.UNKNOWN
        val cpcbRecyclerId = recycler.cpcbRecyclerId ?: return RegistrationValidity.UNKNOWN
        val cpcbRecycler = cpcbRecyclerRepository.findById(cpcbRecyclerId).orElse(null) ?: return RegistrationValidity.UNKNOWN

        val expiryDates = listOfNotNull(
            cpcbRecycler.consentAirExpiry,
            cpcbRecycler.consentWaterExpiry,
            cpcbRecycler.hwmdValidExpiry,
            cpcbRecycler.dicValidExpiry
        )
        if (expiryDates.isEmpty()) return RegistrationValidity.UNKNOWN

        return if (expiryDates.any { it.isBefore(asOfDate) }) RegistrationValidity.EXPIRED else RegistrationValidity.VALID
    }
}
