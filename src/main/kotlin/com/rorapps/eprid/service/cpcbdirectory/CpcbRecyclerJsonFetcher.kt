package com.rorapps.eprid.service.cpcbdirectory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Live pull from CPCB's public battery-recyclerview endpoint
 * (feature_spec_cpcb_directory_refresh.md §1/§3).
 *
 * VERIFIED LIVE, 2026-07-14. `GET /user/recyclerview` is a Laravel-rendered HTML page by default —
 * its own DataTable calls back into that *same URL* via AJAX with DataTables' server-side-processing
 * query params (`draw`/`start`/`length`) and an `X-Requested-With: XMLHttpRequest` header; without
 * those two things Laravel returns the HTML page (not JSON), which is what a plain GET does and
 * why an unheadered request fails closed here. `length=-1` (the DataTable's own "All" page-size
 * option) returns the complete dataset — confirmed 555/555 rows in one call, no pagination loop
 * needed. Field names matched the pre-existing CSV contract (id, uuid, recycler_name,
 * recycler_consent_air, InspectionStatus, ...) exactly, byte for byte, on a live sample row — the
 * mixed snake_case/PascalCase naming really is CPCB's own raw field dump passed straight through.
 * [fetchAll] still fails loudly (throws) rather than silently producing empty/wrong rows if the
 * shape ever changes, so a future CPCB-side change surfaces as a FAILED refresh run, not a silent
 * bad ingest.
 */
@Service
class CpcbRecyclerJsonFetcher(
    @Value("\${app.cpcb.recyclerview-url:https://eprbattery.cpcb.gov.in/user/recyclerview}")
    private val recyclerViewUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = WebClient.builder()
        // Default 256KB in-memory buffer is well under the ~555-row full pull's real size (each
        // row carries ~90 fields). 10MB is generous headroom, not a tuned figure.
        .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
        .defaultHeader("User-Agent", "E-PRid/1.0 (CPCB recycler directory refresh; contact: admin@eprid.in)")
        .build()

    private val flexibleDateFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy MM dd"),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    )

    fun fetchAll(): List<ParsedRecyclerRow> {
        val uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(recyclerViewUrl)
            .queryParam("draw", "1")
            .queryParam("start", "0")
            .queryParam("length", "-1") // DataTable's own "All" page size — full pull in one call
            .build()
            .toUri()

        val body = client.get()
            .uri(uri)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .retrieve()
            .bodyToMono(String::class.java)
            .block(Duration.ofSeconds(30))
            ?: throw IllegalStateException("Empty response from $recyclerViewUrl")

        val root = objectMapper.readTree(body)
        val dataNode = when {
            root.isArray -> root
            root.has("data") && root["data"].isArray -> root["data"]
            else -> throw IllegalStateException(
                "Unrecognized CPCB response shape at $recyclerViewUrl — expected a JSON array or " +
                    "{\"data\": [...]}, got: ${body.take(200)}"
            )
        }

        check(dataNode.size() > 0) { "CPCB response at $recyclerViewUrl parsed but contained zero rows" }

        val rows = dataNode.map { toRow(it) }
        val usable = rows.filter { it.recyclerName.isNotBlank() }
        check(usable.isNotEmpty()) {
            "CPCB response parsed with ${rows.size} row(s) but none had a usable recycler_name — " +
                "field-mapping assumption in CpcbRecyclerJsonFetcher is likely wrong for this endpoint, refusing to proceed"
        }
        if (usable.size < rows.size) {
            log.warn("${rows.size - usable.size} of ${rows.size} CPCB rows had no recycler_name and were skipped")
        }
        return usable
    }

    private fun JsonNode.textOrNull(field: String): String? =
        this[field]?.takeUnless { it.isNull }?.asText()?.trim()?.ifBlank { null }

    private fun JsonNode.dateOrNull(field: String): LocalDate? = textOrNull(field)?.let { raw ->
        flexibleDateFormatters.firstNotNullOfOrNull { fmt ->
            try { LocalDate.parse(raw, fmt) } catch (e: DateTimeParseException) { null }
        }
    }

    private fun JsonNode.instantOrNull(field: String): Instant? =
        textOrNull(field)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun JsonNode.decimalOrNull(field: String): BigDecimal? =
        textOrNull(field)?.let { runCatching { BigDecimal(it) }.getOrNull() }

    private fun JsonNode.intOrNull(field: String): Int? =
        textOrNull(field)?.let { runCatching { it.toDouble().toInt() }.getOrNull() }

    private fun JsonNode.boolOrNull(field: String): Boolean? = textOrNull(field)?.let {
        when (it.trim().lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> null
        }
    }

    private fun toRow(node: JsonNode): ParsedRecyclerRow = ParsedRecyclerRow(
        cpcbId = node.textOrNull("id"),
        cpcbUuid = node.textOrNull("uuid"),
        recyclerName = node.textOrNull("recycler_name") ?: "",
        recyclerAddress = node.textOrNull("recycler_address"),
        stateId = node.textOrNull("state_id"),
        recyclerGstNo = node.textOrNull("recycler_gst_no"),
        consentAirExpiry = node.dateOrNull("recycler_consent_air"),
        consentWaterExpiry = node.dateOrNull("recycler_consent_water"),
        hwmdValidExpiry = node.dateOrNull("recycler_hwmd_valid"),
        dicValidExpiry = node.dateOrNull("recycler_dic_valid"),
        recyclerTypeRaw = node.textOrNull("recycler_type"),
        recyclingCapacity = node.decimalOrNull("recycling_capacity"),
        latitude = node.decimalOrNull("latitude"),
        longitude = node.decimalOrNull("longitude"),
        authorizedName = node.textOrNull("recycler_authorized_name"),
        authorizedEmail = node.textOrNull("recycler_authorized_email"),
        authorizedMobile = node.textOrNull("recycler_authorized_mobile"),
        sourceCreatedAt = node.instantOrNull("created_at"),
        certificateFlag = node.textOrNull("certificate"),
        staffNo = node.intOrNull("Staff_no"),
        workerNo = node.intOrNull("Worker_no"),
        inspectionStatus = node.intOrNull("InspectionStatus"),
        internalAppStatus = node.intOrNull("InternalAppStatus"),
        recyclerWebAddress = node.textOrNull("recycler_web_address"),
        recyclerPhoneNo = node.textOrNull("recycler_phone_no"),
        authorizedPhone = node.textOrNull("recycler_authorized_phone"),
        installedDate = node.dateOrNull("recycler_installed"),
        operatingDate = node.dateOrNull("recycler_operating"),
        iso9001Upload = node.boolOrNull("recycler_iso_9001_upload"),
        iso14001Upload = node.boolOrNull("recycler_iso_14001_upload"),
        apcmUpload = node.boolOrNull("recycler_apcm_upload"),
        wpcmUpload = node.boolOrNull("recycler_wpcm_upload"),
        applicationStatus = node.intOrNull("ApplicationStatus"),
        paymentStatus = node.intOrNull("PaymentStatus"),
        certificateNo = node.textOrNull("certificate_no"),
        certificateDate = node.dateOrNull("certificate_date"),
        sourceUpdatedAt = node.instantOrNull("updated_at"),
        mraiMemb = node.boolOrNull("mrai_memb"),
        sopRecycling = node.boolOrNull("sop_recycling"),
        esgPolicy = node.boolOrNull("esg_policy"),
        websiteLink = node.textOrNull("website_link")
    )
}
