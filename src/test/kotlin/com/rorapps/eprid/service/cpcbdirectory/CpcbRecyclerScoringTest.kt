package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.entity.CpcbGeoRiskHotspot
import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.entity.CpcbRecyclerAuthorization
import com.rorapps.eprid.entity.RiskRating
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Scores real, verbatim rows from eprid_recyclers_seed_sample.csv against the cases the task
 *  spec explicitly calls out — fixed "today" so results don't drift with the calendar. */
class CpcbRecyclerScoringTest {

    private val today = LocalDate.of(2026, 7, 8)

    private val muzaffarnagarHotspot = CpcbGeoRiskHotspot(
        id = "hotspot-1", locationName = "Shamli / Muzaffarnagar, Uttar Pradesh",
        riskLevel = "Medium-High", points = 15,
        latitude = BigDecimal("29.4727"), longitude = BigDecimal("77.7085"), radiusKm = BigDecimal("40")
    )
    private val hotspots = listOf(muzaffarnagarHotspot)

    @Test
    fun `Gotech — everything expired on the same date — scores high or critical, not low`() {
        val gotech = CpcbRecycler(
            id = "r1", recyclerName = "GOTECH BATTERIES AND ALLOYS",
            recyclerGstNo = "37AATFG1466D1Z8",
            consentAirExpiry = LocalDate.of(2025, 2, 28),
            consentWaterExpiry = LocalDate.of(2025, 2, 28),
            hwmdValidExpiry = LocalDate.of(2025, 2, 28),
            dicValidExpiry = LocalDate.of(2025, 2, 28),
            recyclerTypeRaw = null, recyclingCapacity = null,
            latitude = BigDecimal("14.130301"), longitude = BigDecimal("79.769049"),
            staffNo = 0, workerNo = 0,
            dataQualityPartialCapture = false
        )

        val result = CpcbRecyclerScoring.score(gotech, emptyList(), hotspots, today)

        assertTrue(result.flags.any { it.contains("Expired Consent-to-Operate") })
        assertTrue(result.flags.any { it.contains("Expired Hazardous Waste Management") })
        assertTrue(result.flags.any { it.contains("Expired District Industries Centre") })
        assertTrue(result.compositeScore >= 61, "expected High or Critical, got ${result.compositeScore}")
        assertTrue(result.riskBand == RiskRating.HIGH || result.riskBand == RiskRating.CRITICAL)
    }

    @Test
    fun `Mohd Zahid — missing GST on an otherwise well-captured row — flags, does not silently pass`() {
        val mohdZahid = CpcbRecycler(
            id = "r2", recyclerName = "MOHD ZAHID",
            recyclerGstNo = null, // the case under test
            consentAirExpiry = LocalDate.of(2028, 12, 31),
            consentWaterExpiry = LocalDate.of(2028, 12, 31),
            hwmdValidExpiry = LocalDate.of(2028, 12, 31),
            dicValidExpiry = LocalDate.of(2017, 10, 22), // also expired
            recyclerTypeRaw = null, recyclingCapacity = null,
            latitude = BigDecimal("27.825941"), longitude = BigDecimal("80.500166"),
            staffNo = 0, workerNo = 0,
            dataQualityPartialCapture = false // only 3 of 10 fields blank — a real capture, not partial
        )

        val result = CpcbRecyclerScoring.score(mohdZahid, emptyList(), hotspots, today)

        assertTrue(
            result.flags.any { it.contains("No GST number on file") },
            "missing GST on a well-captured row must flag, not be treated as unassessed"
        )
        assertFalse(result.unassessed.any { it.contains("GST") })
    }

    @Test
    fun `Santosh Pigment — missing GST plus expired hazmat and DIC — flags all three`() {
        val santosh = CpcbRecycler(
            id = "r3", recyclerName = "Santosh Pigment & Chemical Industries Pvt. Ltd",
            recyclerGstNo = null,
            consentAirExpiry = LocalDate.of(2027, 12, 31),
            consentWaterExpiry = LocalDate.of(2027, 12, 31),
            hwmdValidExpiry = LocalDate.of(2025, 5, 17),   // expired
            dicValidExpiry = LocalDate.of(2020, 8, 8),      // expired
            recyclerTypeRaw = null, recyclingCapacity = null,
            latitude = BigDecimal("28.48514"), longitude = BigDecimal("77.651867"),
            staffNo = null, workerNo = null,
            dataQualityPartialCapture = false // 5 of 10 blank — below the 6 threshold
        )

        val result = CpcbRecyclerScoring.score(santosh, emptyList(), hotspots, today)

        assertTrue(result.flags.any { it.contains("No GST number on file") })
        assertTrue(result.flags.any { it.contains("Expired Hazardous Waste Management") })
        assertTrue(result.flags.any { it.contains("Expired District Industries Centre") })
    }

    @Test
    fun `Mohd Shahid — expired consent and DIC, GST present — flags expiry only, not GST`() {
        val mohdShahid = CpcbRecycler(
            id = "r4", recyclerName = "MOHD SHAHID",
            recyclerGstNo = "09CKVPS0838P1ZY",
            consentAirExpiry = LocalDate.of(2024, 12, 31),
            consentWaterExpiry = LocalDate.of(2024, 12, 31),
            hwmdValidExpiry = LocalDate.of(2029, 2, 7), // not expired
            dicValidExpiry = LocalDate.of(2024, 12, 11), // expired
            recyclerTypeRaw = null, recyclingCapacity = null,
            latitude = BigDecimal("28.66432"), longitude = BigDecimal("77.17942"),
            staffNo = null, workerNo = null,
            dataQualityPartialCapture = false
        )

        val result = CpcbRecyclerScoring.score(mohdShahid, emptyList(), hotspots, today)

        assertTrue(result.flags.any { it.contains("Expired Consent-to-Operate") })
        assertTrue(result.flags.any { it.contains("Expired District Industries Centre") })
        assertFalse(result.flags.any { it.contains("Hazardous Waste Management") })
        assertFalse(result.flags.any { it.contains("GST") })
        // ~104km from the Muzaffarnagar hotspot center — outside its 40km radius
        assertFalse(result.flags.any { it.contains("hotspot") })
    }

    @Test
    fun `Attero — partial-capture row with real capacity and multi-category authorization — scores clean`() {
        val attero = CpcbRecycler(
            id = "r5", recyclerName = "ATTERO RECYCLING PRIVATE LIMITED",
            cpcbId = null, // missing source id, per the seed
            recyclerGstNo = null, consentAirExpiry = null, consentWaterExpiry = null,
            hwmdValidExpiry = null, dicValidExpiry = null,
            recyclerTypeRaw = "R2: ...#,R3: ...#,R4: ...",
            recyclingCapacity = BigDecimal("7380"), // below the 10,000 capacity-plausibility threshold
            latitude = null, longitude = null,
            staffNo = 0, workerNo = null,
            dataQualityPartialCapture = true // 7 of 10 fields blank — see CpcbRecyclerIngestionServiceTest
        )
        val authorizations = listOf(
            CpcbRecyclerAuthorization(recycler = attero, categoryCode = "R2", categoryLabel = "..."),
            CpcbRecyclerAuthorization(recycler = attero, categoryCode = "R3", categoryLabel = "..."),
            CpcbRecyclerAuthorization(recycler = attero, categoryCode = "R4", categoryLabel = "...")
        )

        val result = CpcbRecyclerScoring.score(attero, authorizations, hotspots, today)

        assertEquals(0, result.compositeScore)
        assertEquals(RiskRating.LOW, result.riskBand)
        assertTrue(result.flags.isEmpty(), "a partial-capture row with no real signal should have zero confirmed flags, got: ${result.flags}")
        assertTrue(result.unassessed.isNotEmpty(), "missing signals must show up as unassessed, not silently absorbed into a clean pass")
    }

    @Test
    fun `Gravita — partial-capture row, only GST present — scores clean, not flagged`() {
        val gravita = CpcbRecycler(
            id = "r6", recyclerName = "GRAVITA INDIA LIMITED",
            cpcbId = "126",
            recyclerGstNo = "08AAACG6753F1ZM",
            consentAirExpiry = null, consentWaterExpiry = null, hwmdValidExpiry = null, dicValidExpiry = null,
            recyclerTypeRaw = null, recyclingCapacity = null,
            latitude = null, longitude = null, staffNo = null, workerNo = null,
            dataQualityPartialCapture = true // 9 of 10 fields blank
        )

        val result = CpcbRecyclerScoring.score(gravita, emptyList(), hotspots, today)

        assertEquals(0, result.compositeScore)
        assertEquals(RiskRating.LOW, result.riskBand)
        assertTrue(result.flags.isEmpty())
    }

    @Test
    fun `high capacity with zero recorded workforce is a soft flag`() {
        val recycler = CpcbRecycler(
            id = "r7", recyclerName = "Big Claimed Capacity Co",
            recyclerGstNo = "GST1", consentAirExpiry = LocalDate.of(2030, 1, 1),
            consentWaterExpiry = LocalDate.of(2030, 1, 1), hwmdValidExpiry = LocalDate.of(2030, 1, 1),
            recyclerTypeRaw = "R1: Lead Acid Battery Recycler",
            recyclingCapacity = BigDecimal("50000"), latitude = null, longitude = null,
            staffNo = 0, workerNo = 0, dataQualityPartialCapture = false
        )

        val result = CpcbRecyclerScoring.score(recycler, emptyList(), hotspots, today)

        assertTrue(result.flags.any { it.contains("no recorded workforce") })
    }

    @Test
    fun `geographic hotspot match adds points and a named flag`() {
        val recycler = CpcbRecycler(
            id = "r8", recyclerName = "Near Muzaffarnagar Co",
            recyclerGstNo = "GST1", consentAirExpiry = LocalDate.of(2030, 1, 1),
            consentWaterExpiry = LocalDate.of(2030, 1, 1), hwmdValidExpiry = LocalDate.of(2030, 1, 1),
            recyclerTypeRaw = "R1: Lead Acid Battery Recycler", recyclingCapacity = BigDecimal("100"),
            latitude = BigDecimal("29.4700"), longitude = BigDecimal("77.7000"), // ~1km from hotspot center
            staffNo = 5, workerNo = 5, dataQualityPartialCapture = false
        )

        val result = CpcbRecyclerScoring.score(recycler, emptyList(), hotspots, today)

        assertTrue(result.flags.any { it.contains("Shamli / Muzaffarnagar") })
        assertTrue(result.compositeScore >= 15)
    }

    @Test
    fun `composite score is capped at 100 even when points would exceed it`() {
        val recycler = CpcbRecycler(
            id = "r9", recyclerName = "Everything Wrong Co",
            recyclerGstNo = null,
            consentAirExpiry = LocalDate.of(2020, 1, 1), consentWaterExpiry = LocalDate.of(2020, 1, 1),
            hwmdValidExpiry = LocalDate.of(2020, 1, 1), dicValidExpiry = LocalDate.of(2020, 1, 1),
            recyclerTypeRaw = null, recyclingCapacity = BigDecimal("50000"),
            latitude = BigDecimal("29.4700"), longitude = BigDecimal("77.7000"),
            staffNo = 0, workerNo = 0, dataQualityPartialCapture = false
        )

        val result = CpcbRecyclerScoring.score(recycler, emptyList(), hotspots, today)

        assertEquals(100, result.compositeScore)
        assertEquals(RiskRating.CRITICAL, result.riskBand)
    }
}
