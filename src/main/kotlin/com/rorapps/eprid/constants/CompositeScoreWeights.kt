package com.rorapps.eprid.constants

import com.rorapps.eprid.entity.RiskRating

/**
 * Composite risk scoring weights (PRD §7.1a) — a draft, uncalibrated mechanism: there is no
 * labeled fraud/clean case set to tune these percentages against yet, same caveat already
 * standing for the Forensics Engine's precision/recall targets. The weighted-sum *mechanism*
 * (explainable per-layer) is what's being locked in now, not these specific numbers.
 *
 * Only BATTERY and TYRE are scored — Module E (used-oil) has no Module A-style check pipeline.
 */
object CompositeScoreWeights {
    data class Weights(
        val registration: Int,
        val capacity: Int,
        val invoice: Int,
        val forensics: Int,
        val regulatory: Int
    )

    val BATTERY = Weights(registration = 20, capacity = 30, invoice = 20, forensics = 20, regulatory = 10)
    val TYRE    = Weights(registration = 25, capacity = 25, invoice = 15, forensics = 25, regulatory = 10)

    /** Neutral default for a signal that hasn't run yet (e.g. no evidence uploaded, regulatory
     *  history not triggered). An "unverified" signal must still count toward the score rather
     *  than being excluded — excluding it would let a bad actor improve their score just by
     *  withholding evidence. */
    const val NEUTRAL_SUB_SCORE = 50

    /** Risk bands (PRD §7.1a) — advisory report language, not directive. Never "reject" / "do not
     *  purchase": the report describes what the signals show, the human still decides. */
    data class Band(val rating: RiskRating, val reportLanguage: String)

    fun bandFor(compositeScore: Int): Band = when {
        compositeScore <= 30 -> Band(
            RiskRating.LOW,
            "Signals are consistent with a legitimate certificate. Most users proceed without further action."
        )
        compositeScore <= 60 -> Band(
            RiskRating.MEDIUM,
            "One or more signals are unverified or borderline. Consider requesting supporting documents before relying on this certificate."
        )
        compositeScore <= 80 -> Band(
            RiskRating.HIGH,
            "Multiple red flags are present. Consider full due diligence, and looping in your compliance team, before relying on this certificate."
        )
        else -> Band(
            RiskRating.CRITICAL,
            "Active fraud indicators are present. Most users in this situation do not rely on this certificate for EPR compliance."
        )
    }
}
