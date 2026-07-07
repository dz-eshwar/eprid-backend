package com.rorapps.eprid.service

import com.rorapps.eprid.constants.CredentialCheckResult
import com.rorapps.eprid.constants.CredentialCheckType
import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.constants.WasteStreamType
import com.rorapps.eprid.entity.*
import com.rorapps.eprid.repository.EvidenceRepository
import com.rorapps.eprid.repository.PlausibilityCheckRepository
import com.rorapps.eprid.repository.RecyclerCredentialCheckRepository
import com.rorapps.eprid.repository.RegulatoryFindingRepository
import com.rorapps.eprid.repository.VerificationCheckRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate

class CompositeScoringServiceTest {

    private val checkRepository            = mock<VerificationCheckRepository>()
    private val plausibilityRepository     = mock<PlausibilityCheckRepository>()
    private val evidenceRepository         = mock<EvidenceRepository>()
    private val credentialCheckRepository  = mock<RecyclerCredentialCheckRepository>()
    private val regulatoryFindingRepository = mock<RegulatoryFindingRepository>()

    private val service = CompositeScoringService(
        checkRepository, plausibilityRepository, evidenceRepository,
        credentialCheckRepository, regulatoryFindingRepository
    )

    private lateinit var user: User
    private lateinit var recycler: Recycler
    private lateinit var producer: Producer

    @BeforeEach
    fun setup() {
        user = User(id = "u1", email = "x@x.com", fullName = "X", passwordHash = "", role = UserRole.CONSULTANT)
        recycler = Recycler(id = "r1", name = "Test Recycler")
        producer = Producer(id = "p1", name = "Test Producer", createdBy = user)

        whenever(checkRepository.save(any<VerificationCheck>())).thenAnswer { it.arguments[0] }
        whenever(plausibilityRepository.findByCheckId(any())).thenReturn(null)
        whenever(evidenceRepository.findAllByCheckId(any())).thenReturn(emptyList())
        whenever(credentialCheckRepository.findAllByRecyclerIdOrderByCheckedAtDesc(any())).thenReturn(emptyList())
        whenever(regulatoryFindingRepository.findAllByCheckId(any())).thenReturn(emptyList())
    }

    private fun makeCheck(wasteStream: WasteStreamType = WasteStreamType.BATTERY) = VerificationCheck(
        id = "c1",
        producer = producer,
        recycler = recycler,
        requestedBy = user,
        wasteStream = wasteStream,
        batchWeightTonnes = BigDecimal("100"),
        claimedRecoveryPct = BigDecimal("75"),
        processingDate = LocalDate.now()
    )

    private fun plausibility(overallStatus: SubCheckStatus, ratio: BigDecimal? = null) = PlausibilityCheck(
        id = "pl1",
        check = makeCheck(),
        claimedRecoveryPct = BigDecimal("75"),
        recoveryStatus = SubCheckStatus.PASS,
        recoveryDetail = "ok",
        recyclerAnnualCapacityT = BigDecimal("1000"),
        batchToCapacityRatio = ratio,
        capacityStatus = SubCheckStatus.PASS,
        capacityDetail = "ok",
        batchWeightT = BigDecimal("100"),
        batchSizeStatus = SubCheckStatus.PASS,
        batchSizeDetail = "ok",
        overallStatus = overallStatus
    )

    // ── neutral defaults when signals haven't run yet ──────────────────────────

    @Test
    fun `all signals neutral when nothing has run yet gives Medium band`() {
        val result = service.recomputeAndSave(makeCheck())
        // all 5 sub-scores default to 50 -> weighted average is 50 -> Medium band (31-60)
        assertEquals(50, result.compositeScore)
        assertEquals(RiskRating.MEDIUM, result.riskRating)
        assertFalse(result.hardDisqualified)
    }

    // ── capacity sub-score driven by plausibility ──────────────────────────────

    @Test
    fun `plausibility PASS lowers composite score toward Low risk`() {
        whenever(plausibilityRepository.findByCheckId("c1")).thenReturn(plausibility(SubCheckStatus.PASS))
        whenever(regulatoryFindingRepository.findAllByCheckId("c1")).thenReturn(emptyList())
        val result = service.recomputeAndSave(makeCheck())
        assertEquals(0, result.capacitySubScore)
        // capacity clean(0) at 30% + rest neutral(50) at 70% = 35 < all-neutral baseline of 50
        assertTrue(result.compositeScore!! < 50)
    }

    @Test
    fun `plausibility FAIL raises composite score toward higher risk`() {
        whenever(plausibilityRepository.findByCheckId("c1")).thenReturn(plausibility(SubCheckStatus.FAIL))
        val result = service.recomputeAndSave(makeCheck())
        assertEquals(100, result.capacitySubScore)
        assertTrue(result.compositeScore!! > 50)
    }

    // ── credential checks (registration sub-score) ─────────────────────────────

    @Test
    fun `any FAILed credential check maxes registration sub-score`() {
        whenever(credentialCheckRepository.findAllByRecyclerIdOrderByCheckedAtDesc("r1")).thenReturn(
            listOf(
                RecyclerCredentialCheck(
                    id = "cc1", recycler = recycler, checkType = CredentialCheckType.GST_VERIFICATION,
                    result = CredentialCheckResult.FAIL, provider = "surepass"
                )
            )
        )
        val result = service.recomputeAndSave(makeCheck())
        assertEquals(100, result.registrationSubScore)
    }

    @Test
    fun `all PASSed credential checks zero registration sub-score`() {
        whenever(credentialCheckRepository.findAllByRecyclerIdOrderByCheckedAtDesc("r1")).thenReturn(
            listOf(
                RecyclerCredentialCheck(
                    id = "cc1", recycler = recycler, checkType = CredentialCheckType.GST_VERIFICATION,
                    result = CredentialCheckResult.PASS, provider = "surepass"
                )
            )
        )
        val result = service.recomputeAndSave(makeCheck())
        assertEquals(0, result.registrationSubScore)
    }

    // ── forensics / invoice sub-scores from evidence ───────────────────────────

    @Test
    fun `failed invoice forensics maxes invoice sub-score`() {
        val invoiceEvidence = Evidence(
            id = "e1", check = makeCheck(), fileName = "invoice.pdf", contentType = "application/pdf",
            fileSizeBytes = 100, storagePath = "/tmp/e1", evidenceType = EvidenceType.INVOICE,
            forensicsStatus = ForensicsStatus.FAIL
        )
        whenever(evidenceRepository.findAllByCheckId("c1")).thenReturn(listOf(invoiceEvidence))
        val result = service.recomputeAndSave(makeCheck())
        assertEquals(100, result.invoiceSubScore)
    }

    // ── regulatory sub-score ────────────────────────────────────────────────────

    @Test
    fun `HIGH regulatory risk maxes regulatory sub-score`() {
        val check = makeCheck().copy(regulatoryRisk = "HIGH")
        val result = service.recomputeAndSave(check)
        assertEquals(100, result.regulatorySubScore)
    }

    // ── hard-disqualification ───────────────────────────────────────────────────

    @Test
    fun `capacity ratio over 3x hard-disqualifies regardless of other signals`() {
        whenever(plausibilityRepository.findByCheckId("c1"))
            .thenReturn(plausibility(SubCheckStatus.FAIL, ratio = BigDecimal("3.5")))
        val result = service.recomputeAndSave(makeCheck())
        assertTrue(result.hardDisqualified)
        assertEquals(100, result.compositeScore)
        assertEquals(RiskRating.CRITICAL, result.riskRating)
        assertNotNull(result.hardDisqualificationReason)
    }

    @Test
    fun `active NGT suspension finding hard-disqualifies`() {
        whenever(regulatoryFindingRepository.findAllByCheckId("c1")).thenReturn(
            listOf(
                RegulatoryFinding(
                    id = "f1", recycler = recycler, check = makeCheck(),
                    source = "NGT", findingType = "SUSPENSION", severity = "HIGH",
                    title = "Suspended", summary = "Recycler suspended by NGT order"
                )
            )
        )
        val result = service.recomputeAndSave(makeCheck())
        assertTrue(result.hardDisqualified)
        assertEquals(RiskRating.CRITICAL, result.riskRating)
    }

    @Test
    fun `low severity finding does not hard-disqualify`() {
        whenever(regulatoryFindingRepository.findAllByCheckId("c1")).thenReturn(
            listOf(
                RegulatoryFinding(
                    id = "f1", recycler = recycler, check = makeCheck(),
                    source = "NEWS", findingType = "NEWS_MENTION", severity = "LOW",
                    title = "Mentioned in article", summary = "Minor mention"
                )
            )
        )
        val result = service.recomputeAndSave(makeCheck())
        assertFalse(result.hardDisqualified)
    }

    // ── used-oil is never scored ────────────────────────────────────────────────

    @Test
    fun `used oil waste stream is skipped entirely`() {
        val check = makeCheck(WasteStreamType.USED_OIL)
        val result = service.recomputeAndSave(check)
        assertNull(result.compositeScore)
        assertFalse(result.hardDisqualified)
        verify(checkRepository, never()).save(any())
    }
}
