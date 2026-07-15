package com.rorapps.eprid.service.cpcbdirectory

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Tests the partial-capture blank-count heuristic in isolation, against the exact real rows the
 *  task's test cases reference — no Spring context / DB needed for this. Logic lives in
 *  [CpcbRecyclerRowMapper] (shared by CSV ingestion and the live refresh pull). */
class CpcbRecyclerIngestionServiceTest {

    private fun row(
        gst: String? = "GST123", consentAir: LocalDate? = LocalDate.of(2028, 1, 1),
        consentWater: LocalDate? = LocalDate.of(2028, 1, 1), hwmd: LocalDate? = LocalDate.of(2028, 1, 1),
        recyclerType: String? = null, capacity: BigDecimal? = null,
        lat: BigDecimal? = BigDecimal("20.0"), lon: BigDecimal? = BigDecimal("80.0"),
        staff: Int? = 0, worker: Int? = 0
    ) = ParsedRecyclerRow(
        cpcbId = "1", cpcbUuid = "uuid", recyclerName = "Test", recyclerAddress = null, stateId = null,
        recyclerGstNo = gst, consentAirExpiry = consentAir, consentWaterExpiry = consentWater,
        hwmdValidExpiry = hwmd, dicValidExpiry = null, recyclerTypeRaw = recyclerType,
        recyclingCapacity = capacity, latitude = lat, longitude = lon, authorizedName = null,
        authorizedEmail = null, authorizedMobile = null, sourceCreatedAt = null, certificateFlag = null,
        staffNo = staff, workerNo = worker, inspectionStatus = null, internalAppStatus = null
    )

    @Test
    fun `a well-captured row with only two blanks is not partial capture`() {
        // mirrors Gotech / Mohd Shahid shape: only recycler_type and capacity blank
        assertFalse(CpcbRecyclerRowMapper.isPartialCapture(row()))
    }

    @Test
    fun `a row missing only GST is not partial capture — GST-missing should flag for real`() {
        // mirrors Mohd Zahid / Santosh
        assertFalse(CpcbRecyclerRowMapper.isPartialCapture(row(gst = null)))
    }

    @Test
    fun `Attero-shaped row — only recycler_type and capacity present, everything else blank — is partial capture`() {
        val attero = row(
            gst = null, consentAir = null, consentWater = null, hwmd = null,
            recyclerType = "R2: ...#,R3: ...#,R4: ...", capacity = BigDecimal("7380"),
            lat = null, lon = null, staff = 0, worker = null
        )
        assertEquals(7, CpcbRecyclerRowMapper.blankFieldCount(attero))
        assertTrue(CpcbRecyclerRowMapper.isPartialCapture(attero))
    }

    @Test
    fun `Gravita-shaped row — only GST present, everything else blank — is partial capture`() {
        val gravita = row(
            gst = "08AAACG6753F1ZM", consentAir = null, consentWater = null, hwmd = null,
            recyclerType = null, capacity = null, lat = null, lon = null, staff = null, worker = null
        )
        assertEquals(9, CpcbRecyclerRowMapper.blankFieldCount(gravita))
        assertTrue(CpcbRecyclerRowMapper.isPartialCapture(gravita))
    }

    @Test
    fun `real India coordinates are plausible`() {
        // Gravita's Rajasthan facility (id 126)
        assertTrue(CpcbRecyclerRowMapper.isPlausibleIndiaGeo(BigDecimal("26.63024"), BigDecimal("75.66822")))
    }

    @Test
    fun `swapped-scale garbage coordinates from the source are rejected, not geocoded`() {
        // id 250 in the 2026-07-08 pull — six-figure values, not real lat/lng at all
        assertFalse(CpcbRecyclerRowMapper.isPlausibleIndiaGeo(BigDecimal("193521.3"), BigDecimal("731013.0")))
    }

    @Test
    fun `a lat-lng pair on the wrong scale for either axis is rejected even if individually digit-plausible`() {
        // id 644 in the 2026-07-08 pull — 24.19282,55.75506 reads like a real coordinate pair,
        // just not one inside India (roughly UAE) — must fail the India bounds check, not pass
        // because both numbers individually look like plausible lat/lng magnitudes.
        assertFalse(CpcbRecyclerRowMapper.isPlausibleIndiaGeo(BigDecimal("24.19282"), BigDecimal("55.75506")))
    }

    @Test
    fun `Reliance-shaped placeholder row — test contact name plus near-empty address — is flagged as likely test data`() {
        val reliance = row().copy(
            recyclerName = "RELIANCE INDUSTRIES LIMITED",
            recyclerAddress = "ABC",
            authorizedName = "TESTING P"
        )
        assertTrue(CpcbRecyclerRowMapper.isLikelyTestRow(reliance))
    }

    @Test
    fun `a legitimately short address alone does not trigger the test-row flag`() {
        // id 317 in the 2026-07-08 pull — "E/15" is a real short plot address, not placeholder data
        val rocklink = row().copy(recyclerAddress = "E/15", authorizedName = "Real Person")
        assertFalse(CpcbRecyclerRowMapper.isLikelyTestRow(rocklink))
    }
}
