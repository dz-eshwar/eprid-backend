package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.entity.CpcbRecycler
import java.math.BigDecimal
import java.time.Instant

/**
 * Row -> entity mapping and data-quality heuristics shared by [CpcbRecyclerIngestionService]
 * (manual CSV upload) and [CpcbRecyclerRefreshService] (scheduled/manual live pull) — both consume
 * the same [ParsedRecyclerRow] contract, so the upsert shape shouldn't fork between the two paths.
 * Extracted from CpcbRecyclerIngestionService (feature_spec_cpcb_directory_refresh.md build).
 */
object CpcbRecyclerRowMapper {

    /**
     * Fields whose combined blankness signals "this row wasn't fully captured in this query
     * pass" rather than "these fields are confirmed absent at CPCB." Threshold picked against
     * the seed data: normal rows (even ones with real expired dates) have 0-3 blanks among
     * these 10; rows like a large player captured without registration/geo data in this pass
     * have 6+. Heuristic, not a CPCB-documented signal — revisit if a full re-pull shows a
     * different pattern.
     */
    const val PARTIAL_CAPTURE_BLANK_THRESHOLD = 6

    fun blankFieldCount(row: ParsedRecyclerRow): Int = listOf(
        row.recyclerGstNo, row.consentAirExpiry, row.consentWaterExpiry, row.hwmdValidExpiry,
        row.recyclerTypeRaw, row.recyclingCapacity, row.latitude, row.longitude,
        row.staffNo, row.workerNo
    ).count { it == null }

    fun isPartialCapture(row: ParsedRecyclerRow): Boolean =
        blankFieldCount(row) >= PARTIAL_CAPTURE_BLANK_THRESHOLD

    /**
     * Rough India bounding box. The full 2026-07-08 pull has 7 rows with implausible lat/lng
     * (swapped-scale values in the hundreds of thousands, a lat/lng pair reversed onto a
     * different continent's scale, etc.) — real CPCB source garbage, not our parsing bug.
     * Rather than geocode against garbage, null the pair out and record what was there.
     */
    private val INDIA_LAT_RANGE = 6.0..37.0
    private val INDIA_LNG_RANGE = 68.0..97.0

    fun isPlausibleIndiaGeo(lat: BigDecimal, lng: BigDecimal): Boolean =
        lat.toDouble() in INDIA_LAT_RANGE && lng.toDouble() in INDIA_LNG_RANGE

    /**
     * One known CPCB test/placeholder entry rides on a real company's name and GST prefix
     * (id 1003, "RELIANCE INDUSTRIES LIMITED", address "ABC", authorized contact "TESTING P")
     * — not a real facility. Flagged, not filtered: a search for "Reliance" should still surface
     * it (so a consultant isn't blindsided by a silently-hidden CPCB row), but callers computing
     * aggregate stats (e.g. average capacity) should exclude flagged rows explicitly.
     */
    fun isLikelyTestRow(row: ParsedRecyclerRow): Boolean =
        row.authorizedName?.contains("test", ignoreCase = true) == true &&
            (row.recyclerAddress?.trim()?.length ?: Int.MAX_VALUE) <= 5

    data class MappedRow(val entity: CpcbRecycler, val isPartialCapture: Boolean)

    /** Pure mapping — no repository/DB access. [existing] is the current DB row for this cpcbId,
     *  if any; the returned entity is unsaved. */
    fun toEntity(row: ParsedRecyclerRow, existing: CpcbRecycler?): MappedRow {
        val blankCount = blankFieldCount(row)
        val partial = isPartialCapture(row)

        val geoValid = row.latitude == null || row.longitude == null ||
            isPlausibleIndiaGeo(row.latitude, row.longitude)
        val (latitude, longitude) = if (geoValid) row.latitude to row.longitude else null to null

        val notes = buildList {
            if (row.cpcbId == null) add(
                "missing_source_id: CPCB row id/uuid not captured — likely a partial-capture " +
                    "artifact, not confirmed to be absent at source"
            )
            if (partial) add(
                "partial_capture: $blankCount of 10 optional fields blank in this row — treat " +
                    "blanks here as unassessed, not confirmed-missing (see CpcbRecyclerRowMapper)"
            )
            if (!geoValid) add(
                "geo_invalid: source lat/lng (${row.latitude}, ${row.longitude}) outside plausible " +
                    "India bounds — nulled out rather than geocoded against garbage"
            )
            if (isLikelyTestRow(row)) add(
                "likely_test_row: address and authorized-contact name both look like CPCB " +
                    "placeholder data, not a real facility — kept in directory, not filtered"
            )
        }.ifEmpty { null }?.joinToString(" | ")

        val entity = (existing ?: CpcbRecycler(recyclerName = row.recyclerName)).copy(
            cpcbId = row.cpcbId,
            cpcbUuid = row.cpcbUuid,
            recyclerName = row.recyclerName,
            recyclerAddress = row.recyclerAddress,
            stateId = row.stateId,
            recyclerGstNo = row.recyclerGstNo,
            consentAirExpiry = row.consentAirExpiry,
            consentWaterExpiry = row.consentWaterExpiry,
            hwmdValidExpiry = row.hwmdValidExpiry,
            dicValidExpiry = row.dicValidExpiry,
            recyclerTypeRaw = row.recyclerTypeRaw,
            recyclingCapacity = row.recyclingCapacity,
            latitude = latitude,
            longitude = longitude,
            authorizedName = row.authorizedName,
            authorizedEmail = row.authorizedEmail,
            authorizedMobile = row.authorizedMobile,
            sourceCreatedAt = row.sourceCreatedAt,
            certificateFlag = row.certificateFlag,
            staffNo = row.staffNo,
            workerNo = row.workerNo,
            inspectionStatus = row.inspectionStatus,
            internalAppStatus = row.internalAppStatus,
            recyclerWebAddress = row.recyclerWebAddress,
            recyclerPhoneNo = row.recyclerPhoneNo,
            authorizedPhone = row.authorizedPhone,
            installedDate = row.installedDate,
            operatingDate = row.operatingDate,
            iso9001Upload = row.iso9001Upload,
            iso14001Upload = row.iso14001Upload,
            apcmUpload = row.apcmUpload,
            wpcmUpload = row.wpcmUpload,
            applicationStatus = row.applicationStatus,
            paymentStatus = row.paymentStatus,
            certificateNo = row.certificateNo,
            certificateDate = row.certificateDate,
            sourceUpdatedAt = row.sourceUpdatedAt,
            mraiMemb = row.mraiMemb,
            sopRecycling = row.sopRecycling,
            esgPolicy = row.esgPolicy,
            websiteLink = row.websiteLink,
            dataQualityPartialCapture = partial,
            dataQualityNotes = notes,
            updatedAt = Instant.now()
        )

        return MappedRow(entity, partial)
    }
}
