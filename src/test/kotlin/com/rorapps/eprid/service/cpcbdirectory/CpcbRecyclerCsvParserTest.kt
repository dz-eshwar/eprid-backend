package com.rorapps.eprid.service.cpcbdirectory

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate

class CpcbRecyclerCsvParserTest {

    private val header = "id,uuid,recycler_name,recycler_address,state_id,recycler_gst_no," +
        "recycler_consent_air,recycler_consent_water,recycler_hwmd_valid,recycler_dic_valid," +
        "recycler_type,recycling_capacity,latitude,longitude,recycler_authorized_name," +
        "recycler_authorized_email,recycler_authorized_mobile,created_at,certificate," +
        "Staff_no,Worker_no,InspectionStatus,InternalAppStatus"

    /** The real seed file (copied verbatim into test resources, not hand-retyped — its Attero row
     *  is a genuinely ragged CSV row, one column short of the header, and that's exactly the case
     *  worth testing against real bytes rather than a manually reconstructed string). */
    private fun realSeedRows() = javaClass.getResourceAsStream("/cpcbdirectory/eprid_recyclers_seed_sample.csv")!!
        .bufferedReader(Charsets.UTF_8).use { CpcbRecyclerCsvParser.parse(it) }

    @Test
    fun `parses all 13 real recycler rows from the seed file without crashing`() {
        val rows = realSeedRows()
        assertEquals(13, rows.size)
    }

    @Test
    fun `Gotech row parses dates, capacity-free fields, and confirmed-zero staff-worker correctly`() {
        val gotech = realSeedRows().single { it.recyclerName == "GOTECH BATTERIES AND ALLOYS" }
        assertEquals("573", gotech.cpcbId)
        assertEquals(LocalDate.of(2025, 2, 28), gotech.consentAirExpiry)
        assertEquals(LocalDate.of(2025, 2, 28), gotech.dicValidExpiry)
        assertEquals(0, gotech.staffNo)
        assertEquals(0, gotech.workerNo)
        assertNull(gotech.inspectionStatus)
        assertEquals(BigDecimal("14.130301"), gotech.latitude)
    }

    @Test
    fun `Mohd Zahid row has blank GST parsed as null, not empty string`() {
        val row = realSeedRows().single { it.recyclerName == "MOHD ZAHID" }
        assertNull(row.recyclerGstNo, "blank GST must parse as null, not empty string")
        assertEquals(0, row.staffNo, "an explicit 0 must stay 0, not become null")
    }

    @Test
    fun `Attero row — real ragged CSV row, one column short of the header — parses without crashing`() {
        val attero = realSeedRows().single { it.recyclerName == "ATTERO RECYCLING PRIVATE LIMITED" }

        assertNull(attero.cpcbId, "Attero's row has no source id in the seed — must not crash or fabricate one")
        assertNull(attero.cpcbUuid)
        assertEquals(BigDecimal("7380"), attero.recyclingCapacity)
        assertEquals(0, attero.staffNo)
        assertNull(attero.workerNo, "blank in the source, not a confirmed zero")
        assertEquals(3, attero.inspectionStatus)
        assertNull(attero.internalAppStatus, "the row is genuinely short this trailing column — must be null, not crash")

        val categories = CpcbRecyclerCsvParser.parseAuthorizations(attero.recyclerTypeRaw)
        assertEquals(3, categories.size, "the embedded plain comma inside R4's own text must not split it into a 4th category")
        assertEquals("R2", categories[0].categoryCode)
        assertEquals("R3", categories[1].categoryCode)
        assertEquals("R4", categories[2].categoryCode)
        assertTrue(categories[2].categoryLabel.contains("Physical Separation and Refining"))
    }

    @Test
    fun `Gravita row parses with almost every optional field blank`() {
        val gravita = realSeedRows().single { it.recyclerName == "GRAVITA INDIA LIMITED" }
        assertEquals("126", gravita.cpcbId)
        assertEquals("08AAACG6753F1ZM", gravita.recyclerGstNo)
        assertNull(gravita.recyclerTypeRaw)
        assertNull(gravita.recyclingCapacity)
        assertNull(gravita.latitude)
    }

    @Test
    fun `single-category recycler_type with no hash-delimiter parses as one category`() {
        val categories = CpcbRecyclerCsvParser.parseAuthorizations("R1: Lead Acid Battery Recycler")
        assertEquals(1, categories.size)
        assertEquals("R1", categories[0].categoryCode)
        assertEquals("Lead Acid Battery Recycler", categories[0].categoryLabel)
    }

    @Test
    fun `blank recycler_type parses as no categories`() {
        assertTrue(CpcbRecyclerCsvParser.parseAuthorizations(null).isEmpty())
        assertTrue(CpcbRecyclerCsvParser.parseAuthorizations("").isEmpty())
    }

    @Test
    fun `blank trailing line is skipped, not parsed as an empty-name row`() {
        val csv = header + "\n" +
            "126,bb51ac5e-3bcf-41ed-ab92-8a03d369e6f6,GRAVITA INDIA LIMITED,Address,19," +
            "08AAACG6753F1ZM,,,,,,,,,,,,,,,,,\n\n"

        val rows = CpcbRecyclerCsvParser.parse(StringReader(csv))
        assertEquals(1, rows.size)
        assertEquals("GRAVITA INDIA LIMITED", rows[0].recyclerName)
    }
}
