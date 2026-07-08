package com.rorapps.eprid.service.cpcbdirectory

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.Reader
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One parsed CSV row, before any data-quality/scoring logic runs. Field names mirror the CSV
 *  header exactly (see eprid_recyclers_seed_sample.csv) — this is the ingestion contract; where
 *  the CSV actually comes from (this seed today, a full re-pull later) shouldn't matter here. */
data class ParsedRecyclerRow(
    val cpcbId: String?,
    val cpcbUuid: String?,
    val recyclerName: String,
    val recyclerAddress: String?,
    val stateId: String?,
    val recyclerGstNo: String?,
    val consentAirExpiry: LocalDate?,
    val consentWaterExpiry: LocalDate?,
    val hwmdValidExpiry: LocalDate?,
    val dicValidExpiry: LocalDate?,
    val recyclerTypeRaw: String?,
    val recyclingCapacity: BigDecimal?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val authorizedName: String?,
    val authorizedEmail: String?,
    val authorizedMobile: String?,
    val sourceCreatedAt: Instant?,
    val certificateFlag: String?,
    val staffNo: Int?,
    val workerNo: Int?,
    val inspectionStatus: Int?,
    val internalAppStatus: Int?
)

data class ParsedAuthorization(val categoryCode: String, val categoryLabel: String)

object CpcbRecyclerCsvParser {

    private val REQUIRED_HEADERS = listOf(
        "id", "uuid", "recycler_name", "recycler_address", "state_id", "recycler_gst_no",
        "recycler_consent_air", "recycler_consent_water", "recycler_hwmd_valid", "recycler_dic_valid",
        "recycler_type", "recycling_capacity", "latitude", "longitude",
        "recycler_authorized_name", "recycler_authorized_email", "recycler_authorized_mobile",
        "created_at", "certificate", "Staff_no", "Worker_no", "InspectionStatus", "InternalAppStatus"
    )

    fun parse(reader: Reader): List<ParsedRecyclerRow> {
        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build()

        CSVParser.parse(reader, format).use { parser ->
            val headerMap = parser.headerNames
            val missing = REQUIRED_HEADERS.filterNot { it in headerMap }
            require(missing.isEmpty()) { "CSV missing required columns: $missing" }

            return parser.records
                // Fully blank rows (a trailing empty line) have no name — skip, not a data row
                .filter { it.get("recycler_name")?.isNotBlank() == true }
                .map { record ->
                    ParsedRecyclerRow(
                        cpcbId = record.blankToNull("id"),
                        cpcbUuid = record.blankToNull("uuid"),
                        recyclerName = record.get("recycler_name").trim(),
                        recyclerAddress = record.blankToNull("recycler_address"),
                        stateId = record.blankToNull("state_id"),
                        recyclerGstNo = record.blankToNull("recycler_gst_no"),
                        consentAirExpiry = record.blankToDate("recycler_consent_air"),
                        consentWaterExpiry = record.blankToDate("recycler_consent_water"),
                        hwmdValidExpiry = record.blankToDate("recycler_hwmd_valid"),
                        dicValidExpiry = record.blankToDate("recycler_dic_valid"),
                        recyclerTypeRaw = record.blankToNull("recycler_type"),
                        recyclingCapacity = record.blankToBigDecimal("recycling_capacity"),
                        latitude = record.blankToBigDecimal("latitude"),
                        longitude = record.blankToBigDecimal("longitude"),
                        authorizedName = record.blankToNull("recycler_authorized_name"),
                        authorizedEmail = record.blankToNull("recycler_authorized_email"),
                        authorizedMobile = record.blankToNull("recycler_authorized_mobile"),
                        sourceCreatedAt = record.blankToInstant("created_at"),
                        certificateFlag = record.blankToNull("certificate"),
                        staffNo = record.blankToInt("Staff_no"),
                        workerNo = record.blankToInt("Worker_no"),
                        inspectionStatus = record.blankToInt("InspectionStatus"),
                        internalAppStatus = record.blankToInt("InternalAppStatus")
                    )
                }
        }
    }

    /**
     * CPCB joins multi-category authorizations with a literal "#," between entries (not a comma
     * alone — plain commas can appear inside a category's own description text, e.g.
     * "Physical Separation and Refining" — so splitting on bare "," would corrupt that segment).
     * A single-category string (e.g. "R1: Lead Acid Battery Recycler") has no "#," at all.
     */
    fun parseAuthorizations(raw: String?): List<ParsedAuthorization> {
        if (raw.isNullOrBlank()) return emptyList()
        val categoryPattern = Regex("^(R\\d+):\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)
        return raw.split("#,")
            .map { it.trim().removeSuffix("#").trim() }
            .filter { it.isNotBlank() }
            .map { segment ->
                val match = categoryPattern.find(segment)
                if (match != null) {
                    ParsedAuthorization(categoryCode = match.groupValues[1], categoryLabel = match.groupValues[2].trim())
                } else {
                    ParsedAuthorization(categoryCode = "UNKNOWN", categoryLabel = segment)
                }
            }
    }

    /** Real CPCB exports include ragged rows — fewer trailing values than header columns (seen in
     *  the seed's Attero row, which is short its last column). Treat an out-of-range trailing
     *  column the same as a blank one rather than crashing the whole ingestion on one bad row. */
    private fun org.apache.commons.csv.CSVRecord.blankToNull(column: String): String? =
        if (isMapped(column) && isSet(column)) get(column)?.trim()?.ifBlank { null } else null

    private fun org.apache.commons.csv.CSVRecord.blankToDate(column: String): LocalDate? =
        blankToNull(column)?.let {
            try { LocalDate.parse(it) } catch (e: DateTimeParseException) { null }
        }

    private fun org.apache.commons.csv.CSVRecord.blankToInstant(column: String): Instant? =
        blankToNull(column)?.let {
            try { Instant.parse(it) } catch (e: DateTimeParseException) { null }
        }

    private fun org.apache.commons.csv.CSVRecord.blankToBigDecimal(column: String): BigDecimal? =
        blankToNull(column)?.let {
            try { BigDecimal(it) } catch (e: NumberFormatException) { null }
        }

    private fun org.apache.commons.csv.CSVRecord.blankToInt(column: String): Int? =
        blankToNull(column)?.let {
            try { it.toDouble().toInt() } catch (e: NumberFormatException) { null }
        }
}
