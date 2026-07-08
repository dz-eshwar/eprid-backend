package com.rorapps.eprid.service.cpcbdirectory

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Tests the partial-capture blank-count heuristic in isolation, against the exact real rows the
 *  task's test cases reference — no Spring context / DB needed for this. */
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
        assertFalse(CpcbRecyclerIngestionService.isPartialCapture(row()))
    }

    @Test
    fun `a row missing only GST is not partial capture — GST-missing should flag for real`() {
        // mirrors Mohd Zahid / Santosh
        assertFalse(CpcbRecyclerIngestionService.isPartialCapture(row(gst = null)))
    }

    @Test
    fun `Attero-shaped row — only recycler_type and capacity present, everything else blank — is partial capture`() {
        val attero = row(
            gst = null, consentAir = null, consentWater = null, hwmd = null,
            recyclerType = "R2: ...#,R3: ...#,R4: ...", capacity = BigDecimal("7380"),
            lat = null, lon = null, staff = 0, worker = null
        )
        assertEquals(7, CpcbRecyclerIngestionService.blankFieldCount(attero))
        assertTrue(CpcbRecyclerIngestionService.isPartialCapture(attero))
    }

    @Test
    fun `Gravita-shaped row — only GST present, everything else blank — is partial capture`() {
        val gravita = row(
            gst = "08AAACG6753F1ZM", consentAir = null, consentWater = null, hwmd = null,
            recyclerType = null, capacity = null, lat = null, lon = null, staff = null, worker = null
        )
        assertEquals(9, CpcbRecyclerIngestionService.blankFieldCount(gravita))
        assertTrue(CpcbRecyclerIngestionService.isPartialCapture(gravita))
    }
}
