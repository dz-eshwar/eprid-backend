package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.entity.CpcbGeoRiskHotspot
import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.entity.CpcbRecyclerAuthorization
import com.rorapps.eprid.entity.RiskRating
import com.rorapps.eprid.entity.ScoreConfidence
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CpcbScoreResult(
    val compositeScore: Int,
    val riskBand: RiskRating,
    val flags: List<String>,
    val unassessed: List<String>,
    val layerBreakdown: Map<String, Any?>,
    val scoreConfidence: ScoreConfidence = ScoreConfidence.ENTITY_HEALTH
)

/**
 * Entity Health Score — registration/authorization/geography only (this data source has no
 * certificate-volume, Form 4, or invoice data; that's Certificate Risk Score, a separate,
 * not-yet-built thing — see product_document_built_state.md).
 *
 * Point values below are additive, baseline 0, capped [0,100]. Only "expired CTO" (+40) and
 * "expired hazmat authorization" (+40) come directly from spec; every other exact number
 * (DIC minor flag, GST-missing, ISO bonus, chemistry-unclear, capacity/workforce mismatch) is an
 * assumed placeholder — the spec described these qualitatively ("minor flag", "small positive
 * adjustment", "soft signal") without giving exact points. Adjust once real fraud/clean cases
 * exist to calibrate against — same uncalibrated-draft caveat as the per-check composite scoring
 * in CompositeScoreWeights.
 *
 * `recycling_capacity` units are NOT confirmed against CPCB documentation (likely MT/year given
 * observed ranges, unverified) — the >10,000 threshold below is a raw-number comparison, not a
 * unit-aware one. Flagging this explicitly per the task spec rather than guessing a conversion.
 */
object CpcbRecyclerScoring {

    private const val EXPIRED_CONSENT_POINTS = 40
    private const val EXPIRED_HWMD_POINTS = 40
    private const val EXPIRED_DIC_POINTS = 10          // assumed "minor" value
    private const val MISSING_GST_POINTS = 15          // assumed value
    private const val ISO_BOTH_PRESENT_BONUS = -10      // assumed value
    private const val CHEMISTRY_UNCLEAR_POINTS = 10     // assumed value
    private const val CAPACITY_WORKFORCE_MISMATCH_POINTS = 15  // assumed value
    private val CAPACITY_THRESHOLD = BigDecimal(10_000) // raw number, units unconfirmed

    /**
     * Fields whose combined blankness marks a row as "partial capture" (see
     * CpcbRecyclerIngestionService) — when true, a blank GST/recycler_type is treated as
     * unassessed rather than a confirmed gap, since the whole row likely wasn't fully queried.
     */
    fun score(
        recycler: CpcbRecycler,
        authorizations: List<CpcbRecyclerAuthorization>,
        hotspots: List<CpcbGeoRiskHotspot>,
        today: LocalDate = LocalDate.now()
    ): CpcbScoreResult {
        val flags = mutableListOf<String>()
        val unassessed = mutableListOf<String>()
        var points = 0

        // ── Registration / authorization health ────────────────────────────────────────────
        val registrationBreakdown = mutableMapOf<String, Any?>()

        val consentExpired = listOfNotNull(recycler.consentAirExpiry, recycler.consentWaterExpiry)
            .any { it.isBefore(today) }
        if (consentExpired) {
            points += EXPIRED_CONSENT_POINTS
            flags += "Expired Consent-to-Operate (air and/or water)"
        }
        registrationBreakdown["consentExpired"] = consentExpired

        val hwmdExpired = recycler.hwmdValidExpiry?.isBefore(today) == true
        if (hwmdExpired) {
            points += EXPIRED_HWMD_POINTS
            flags += "Expired Hazardous Waste Management authorization"
        }
        registrationBreakdown["hwmdExpired"] = hwmdExpired

        val dicExpired = recycler.dicValidExpiry?.isBefore(today) == true
        if (dicExpired) {
            points += EXPIRED_DIC_POINTS
            flags += "Expired District Industries Centre validity"
        }
        if (recycler.dicValidExpiry == null) unassessed += "DIC validity (not captured)"
        registrationBreakdown["dicExpired"] = dicExpired

        when {
            recycler.recyclerGstNo != null -> registrationBreakdown["gstPresent"] = true
            recycler.dataQualityPartialCapture ->
                unassessed += "GST number (row partially captured — not confirmed missing at source)"
            else -> {
                points += MISSING_GST_POINTS
                flags += "No GST number on file — cannot verify registration"
            }
        }

        // The full CSV contract (2026-07-08 pull) does have recycler_iso_9001_upload/
        // recycler_iso_14001_upload columns, but all 553 rows have them blank at source — so this
        // is wired up and correct, just inert until CPCB actually starts populating them. NULL is
        // treated the same as false (no bonus), not guessed at true.
        val isoBothOnFile = recycler.iso9001Upload == true && recycler.iso14001Upload == true
        if (isoBothOnFile) {
            points += ISO_BOTH_PRESENT_BONUS
            flags += "ISO 9001 and 14001 both on file"
        }
        registrationBreakdown["isoBothOnFile"] = isoBothOnFile

        // ── Chemistry / process authorization clarity (Layer 1 proxy) ──────────────────────
        val chemistryBreakdown = mutableMapOf<String, Any?>()
        chemistryBreakdown["categories"] = authorizations.map { "${it.categoryCode}: ${it.categoryLabel}" }
        when {
            authorizations.isNotEmpty() -> { /* descriptive only — multi-category is normal, not risky */ }
            recycler.dataQualityPartialCapture ->
                unassessed += "Chemistry/process authorization (row partially captured)"
            else -> {
                points += CHEMISTRY_UNCLEAR_POINTS
                flags += "No chemistry/process authorization category on file"
            }
        }

        // ── Capacity plausibility (Layer 5 proxy — new heuristic, not in original methodology) ─
        val capacityBreakdown = mutableMapOf<String, Any?>()
        capacityBreakdown["recyclingCapacity"] = recycler.recyclingCapacity
        capacityBreakdown["staffNo"] = recycler.staffNo
        capacityBreakdown["workerNo"] = recycler.workerNo
        val noWorkforceRecorded = (recycler.staffNo == null || recycler.staffNo == 0) &&
            (recycler.workerNo == null || recycler.workerNo == 0)
        val highCapacityNoWorkforce = recycler.recyclingCapacity != null &&
            recycler.recyclingCapacity > CAPACITY_THRESHOLD && noWorkforceRecorded
        if (highCapacityNoWorkforce) {
            points += CAPACITY_WORKFORCE_MISMATCH_POINTS
            flags += "Declared capacity >${CAPACITY_THRESHOLD} with no recorded workforce — soft signal, not a hard disqualifier"
        }
        if (recycler.recyclingCapacity == null) {
            unassessed += "Capacity plausibility (recycling_capacity not captured)"
        }

        // ── Geographic risk ─────────────────────────────────────────────────────────────────
        val geoBreakdown = mutableMapOf<String, Any?>()
        var geoPoints = 0
        var matchedHotspot: CpcbGeoRiskHotspot? = null
        if (recycler.latitude != null && recycler.longitude != null) {
            val match = hotspots
                .map { it to haversineKm(recycler.latitude, recycler.longitude, it.latitude, it.longitude) }
                .filter { (hotspot, distanceKm) -> distanceKm <= hotspot.radiusKm.toDouble() }
                .maxByOrNull { (hotspot, _) -> hotspot.points }
            if (match != null) {
                matchedHotspot = match.first
                geoPoints = match.first.points
                points += geoPoints
                flags += "Located within known geographic risk hotspot: ${match.first.locationName} (${match.first.riskLevel})"
            }
        } else {
            unassessed += "Geographic risk (latitude/longitude not captured)"
        }
        geoBreakdown["matchedHotspot"] = matchedHotspot?.locationName
        geoBreakdown["points"] = geoPoints

        val composite = points.coerceIn(0, 100)
        val band = when {
            composite <= 30 -> RiskRating.LOW
            composite <= 60 -> RiskRating.MEDIUM
            composite <= 80 -> RiskRating.HIGH
            else -> RiskRating.CRITICAL
        }

        return CpcbScoreResult(
            compositeScore = composite,
            riskBand = band,
            flags = flags,
            unassessed = unassessed,
            layerBreakdown = mapOf(
                "registration" to registrationBreakdown,
                "chemistry" to chemistryBreakdown,
                "capacity" to capacityBreakdown,
                "geographic" to geoBreakdown,
                "note" to "Layer 2 (metal-yield) and Layer 4 (invoice traceability) are not assessed — " +
                    "this data source has no certificate-volume or invoice data."
            )
        )
    }

    /** Great-circle distance in km — hotspot match is a point+radius proxy for a district, not
     *  real boundary data (see the migration's comment on cpcb_geo_risk_hotspots). */
    private fun haversineKm(lat1: BigDecimal, lon1: BigDecimal, lat2: BigDecimal, lon2: BigDecimal): Double {
        val r = 6371.0
        val dLat = Math.toRadians((lat2 - lat1).toDouble())
        val dLon = Math.toRadians((lon2 - lon1).toDouble())
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1.toDouble())) * cos(Math.toRadians(lat2.toDouble())) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
