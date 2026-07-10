package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.BatteryMetal
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/** One row per (check, metal) — the recycler's self-declared recovered weight for that metal.
 *  Today this only existed as a single aggregate [VerificationCheck.claimedRecoveryPct]. */
@Entity
@Table(name = "claimed_metal_recoveries", schema = "eprid")
data class ClaimedMetalRecovery(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    val check: VerificationCheck,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val metal: BatteryMetal,

    @Column(name = "claimed_weight_kg", nullable = false, precision = 12, scale = 3)
    val claimedWeightKg: BigDecimal,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
