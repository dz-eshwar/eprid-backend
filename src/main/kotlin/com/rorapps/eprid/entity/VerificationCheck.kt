package com.rorapps.eprid.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class CheckStatus { PENDING, RUNNING, COMPLETE, FAILED }
enum class RiskRating { LOW, MEDIUM, HIGH }
enum class RegulatoryStatus { NOT_STARTED, PENDING, COMPLETE, FAILED }

@Entity
@Table(name = "verification_checks", schema = "eprid")
data class VerificationCheck(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producer_id", nullable = false)
    val producer: Producer,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycler_id", nullable = false)
    val recycler: Recycler,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    val requestedBy: User,

    /** The calculator session that prompted this check — nullable if check was started directly */
    @Column(name = "compliance_estimate_id", nullable = true)
    val complianceEstimateId: String? = null,

    @Column(nullable = false, precision = 12, scale = 3)
    val batchWeightTonnes: BigDecimal,

    @Column(nullable = false, precision = 5, scale = 2)
    val claimedRecoveryPct: BigDecimal,

    @Column(nullable = false)
    val processingDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: CheckStatus = CheckStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    val riskRating: RiskRating? = null,

    @Column(nullable = true, columnDefinition = "TEXT")
    val riskSummary: String? = null,

    // ── Regulatory history fields (populated by Agent 5) ──────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val regulatoryStatus: RegulatoryStatus = RegulatoryStatus.NOT_STARTED,

    @Column(nullable = true)
    val regulatoryRisk: String? = null,      // LOW | MEDIUM | HIGH | UNKNOWN

    @Column(nullable = true, columnDefinition = "TEXT")
    val regulatorySummary: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = true)
    val completedAt: Instant? = null
)
