package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.BatteryChemistry
import com.rorapps.eprid.constants.TyreEndProduct
import com.rorapps.eprid.constants.WasteStreamType
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class CheckStatus { PENDING, RUNNING, COMPLETE, FAILED }
enum class RiskRating { LOW, MEDIUM, HIGH, CRITICAL }
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
    @Column(name = "waste_stream", nullable = false)
    val wasteStream: WasteStreamType = WasteStreamType.BATTERY,

    /** Tyre only: quantity of end-product sold (QP in CPCB's QEPR = QP × CF × WP formula, §7.5).
     *  Unit depends on [tyreEndProduct] (kg for solids, litres for pyrolysis oil). Null for battery checks. */
    @Column(name = "claimed_output_quantity", nullable = true, precision = 12, scale = 3)
    val claimedOutputQuantity: BigDecimal? = null,

    /** Tyre only: which end-product category was sold — selects CF/WP from CPCB's table (§7.5). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tyre_end_product", nullable = true)
    val tyreEndProduct: TyreEndProduct? = null,

    /** Tyre only: true if the underlying waste tyre was imported — forces WP = 1.0 per CPCB's formula. */
    @Column(name = "tyre_imported", nullable = false)
    val tyreImported: Boolean = false,

    /** Tyre only: the recycler's claimed EPR certificate credit (kg) for this batch, reconciled
     *  against the CF×WP-computed QEPR. This is the figure the plausibility check is verifying. */
    @Column(name = "claimed_epr_credit_kg", nullable = true, precision = 12, scale = 3)
    val claimedEprCreditKg: BigDecimal? = null,

    /** Battery only: chemistry declared for this batch — drives the composition-table check (§1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "declared_battery_chemistry", nullable = true)
    val declaredBatteryChemistry: BatteryChemistry? = null,

    /** Date the underlying certificate was actually issued — distinct from [createdAt] (when this
     *  check was run, which can be months later). Used as the as-of date for registration-validity
     *  hard-disqualification (§2 rule 1). Falls back to [processingDate] when not supplied. */
    @Column(name = "certificate_date", nullable = true)
    val certificateDate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: CheckStatus = CheckStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    val riskRating: RiskRating? = null,

    @Column(nullable = true, columnDefinition = "TEXT")
    val riskSummary: String? = null,

    // ── Composite risk scoring (§7.1a) — recomputed after plausibility, evidence upload,
    //    and regulatory history each complete, since those signals arrive at different times ──
    @Column(name = "composite_score", nullable = true)
    val compositeScore: Int? = null,

    @Column(name = "registration_sub_score", nullable = true)
    val registrationSubScore: Int? = null,

    @Column(name = "capacity_sub_score", nullable = true)
    val capacitySubScore: Int? = null,

    @Column(name = "invoice_sub_score", nullable = true)
    val invoiceSubScore: Int? = null,

    @Column(name = "forensics_sub_score", nullable = true)
    val forensicsSubScore: Int? = null,

    @Column(name = "regulatory_sub_score", nullable = true)
    val regulatorySubScore: Int? = null,

    @Column(name = "hard_disqualified", nullable = false)
    val hardDisqualified: Boolean = false,

    @Column(name = "hard_disqualification_reason", nullable = true, columnDefinition = "TEXT")
    val hardDisqualificationReason: String? = null,

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
