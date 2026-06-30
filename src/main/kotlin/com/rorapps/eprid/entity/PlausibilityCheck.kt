package com.rorapps.eprid.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

enum class SubCheckStatus { PASS, WARN, FAIL, UNVERIFIABLE }

@Entity
@Table(name = "plausibility_checks", schema = "eprid")
data class PlausibilityCheck(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false, unique = true)
    val check: VerificationCheck,

    // ── Recovery rate ─────────────────────────────────────────────────────────
    @Column(nullable = false, precision = 5, scale = 2)
    val claimedRecoveryPct: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val recoveryStatus: SubCheckStatus,

    @Column(nullable = false, columnDefinition = "TEXT")
    val recoveryDetail: String,

    // ── Capacity ceiling ──────────────────────────────────────────────────────
    @Column(name = "recycler_annual_capacity_t", nullable = true, precision = 12, scale = 3)
    val recyclerAnnualCapacityT: BigDecimal?,

    @Column(nullable = true, precision = 7, scale = 4)
    val batchToCapacityRatio: BigDecimal?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val capacityStatus: SubCheckStatus,

    @Column(nullable = false, columnDefinition = "TEXT")
    val capacityDetail: String,

    // ── Absolute batch size ───────────────────────────────────────────────────
    @Column(name = "batch_weight_t", nullable = false, precision = 12, scale = 3)
    val batchWeightT: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val batchSizeStatus: SubCheckStatus,

    @Column(nullable = false, columnDefinition = "TEXT")
    val batchSizeDetail: String,

    // ── Overall ───────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val overallStatus: SubCheckStatus,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
