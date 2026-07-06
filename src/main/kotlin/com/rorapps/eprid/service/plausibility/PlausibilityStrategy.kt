package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.dto.plausibility.PlausibilitySubCheck
import com.rorapps.eprid.entity.VerificationCheck

/**
 * Pluggable per-waste-stream plausibility logic. Each implementation is a Spring bean;
 * [PlausibilityCheckService] picks the one that [supports] a given check's waste stream.
 * Removing a waste stream module means deleting its strategy implementation — nothing else changes.
 */
interface PlausibilityStrategy {
    fun supports(wasteStream: WasteStreamType): Boolean

    /** Must return exactly 3 sub-checks, in order: [recovery/yield-style check, capacity ceiling, absolute batch size] —
     *  the router persists them positionally into PlausibilityCheck's existing 3 column-pairs. */
    fun runChecks(check: VerificationCheck): List<PlausibilitySubCheck>
}
