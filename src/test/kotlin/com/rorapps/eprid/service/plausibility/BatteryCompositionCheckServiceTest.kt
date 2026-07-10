package com.rorapps.eprid.service.plausibility

import com.rorapps.eprid.constants.BatteryChemistry
import com.rorapps.eprid.constants.BatteryMetal
import com.rorapps.eprid.constants.CompositionCheckResult
import com.rorapps.eprid.entity.*
import com.rorapps.eprid.repository.MetalCompositionCheckRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class BatteryCompositionCheckServiceTest {

    private val repository = mock<MetalCompositionCheckRepository>()
    private val service = BatteryCompositionCheckService(repository)

    private val user = User(id = "u1", email = "x@x.com", fullName = "X", passwordHash = "", role = UserRole.CONSULTANT)
    private val recycler = Recycler(id = "r1", name = "Test Recycler")
    private val producer = Producer(id = "p1", name = "Test Producer", createdBy = user)

    init {
        whenever(repository.saveAll(any<List<MetalCompositionCheck>>())).thenAnswer { it.arguments[0] }
    }

    private fun makeCheck(chemistry: BatteryChemistry?, batchWeightTonnes: BigDecimal = BigDecimal("1")) = VerificationCheck(
        id = "c1", producer = producer, recycler = recycler, requestedBy = user,
        wasteStream = com.rorapps.eprid.constants.WasteStreamType.BATTERY,
        batchWeightTonnes = batchWeightTonnes, claimedRecoveryPct = BigDecimal("75"),
        processingDate = LocalDate.now(), declaredBatteryChemistry = chemistry
    )

    private fun recovery(check: VerificationCheck, metal: BatteryMetal, kg: String) =
        ClaimedMetalRecovery(check = check, metal = metal, claimedWeightKg = BigDecimal(kg))

    @Test
    fun `no declared chemistry skips the check entirely`() {
        val check = makeCheck(chemistry = null)
        val results = service.runAndSave(check, emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `lead-acid batch with claimed lithium is a zero-cell violation`() {
        val check = makeCheck(BatteryChemistry.LEAD_ACID, batchWeightTonnes = BigDecimal("1")) // 1000 kg batch
        val claimed = listOf(
            recovery(check, BatteryMetal.PB, "700"),  // 70% - in range
            recovery(check, BatteryMetal.LI, "10")    // 1% - LEAD_ACID expects 0% Li
        )
        val results = service.runAndSave(check, claimed)

        val liResult = results.first { it.metal == BatteryMetal.LI }
        assertEquals(CompositionCheckResult.ZERO_CELL_VIOLATION, liResult.result)

        val pbResult = results.first { it.metal == BatteryMetal.PB }
        assertEquals(CompositionCheckResult.PASS, pbResult.result)
    }

    @Test
    fun `lithium-ion batch with all metals in range all pass`() {
        val check = makeCheck(BatteryChemistry.LITHIUM_ION, batchWeightTonnes = BigDecimal("1"))
        val claimed = listOf(
            recovery(check, BatteryMetal.LI, "30"),   // 3%
            recovery(check, BatteryMetal.MN, "100"),  // 10%
            recovery(check, BatteryMetal.NI, "100"),  // 10%
            recovery(check, BatteryMetal.CO, "100"),  // 10%
            recovery(check, BatteryMetal.AL, "150"),  // 15%
            recovery(check, BatteryMetal.FE, "200"),  // 20%
            recovery(check, BatteryMetal.CU, "100"),  // 10%
            recovery(check, BatteryMetal.PB, "0"),
            recovery(check, BatteryMetal.ZN, "5"),    // 0.5%
            recovery(check, BatteryMetal.CD, "0")
        )
        val results = service.runAndSave(check, claimed)
        assertTrue(results.all { it.result == CompositionCheckResult.PASS })
    }

    @Test
    fun `metal with no claimed weight is could-not-verify, not a silent pass`() {
        val check = makeCheck(BatteryChemistry.LEAD_ACID, batchWeightTonnes = BigDecimal("1"))
        val claimed = listOf(recovery(check, BatteryMetal.PB, "700"))
        val results = service.runAndSave(check, claimed)

        val alResult = results.first { it.metal == BatteryMetal.AL }
        assertEquals(CompositionCheckResult.COULD_NOT_VERIFY, alResult.result)
    }
}
