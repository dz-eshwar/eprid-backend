package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.BatteryMetal
import com.rorapps.eprid.constants.CompositionCheckResult
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/** One row per (check, metal) — result of comparing the claimed recovery % for that metal
 *  against CPCB's published composition range for the check's declared chemistry (PRD §7.1). */
@Entity
@Table(name = "metal_composition_checks", schema = "eprid")
data class MetalCompositionCheck(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    val check: VerificationCheck,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val metal: BatteryMetal,

    // scale 4 (not 3) to hold the computed percentage without truncation — claimed metal weight is
    // kg against a tonnes-scale batch, so realistic percentages are frequently sub-0.01%.
    @Column(name = "claimed_pct", nullable = true, precision = 9, scale = 4)
    val claimedPct: BigDecimal?,

    @Column(name = "expected_min", nullable = false, precision = 6, scale = 3)
    val expectedMin: BigDecimal,

    @Column(name = "expected_max", nullable = false, precision = 6, scale = 3)
    val expectedMax: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val result: CompositionCheckResult,

    @Column(nullable = false, columnDefinition = "TEXT")
    val detail: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
