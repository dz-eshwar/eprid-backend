package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.BatteryCategory
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "compliance_estimates", schema = "eprid")
data class ComplianceEstimate(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val batteryCategory: BatteryCategory,

    @Column(nullable = false, length = 10)
    val financialYear: String,

    @Column(nullable = false, precision = 12, scale = 3)
    val quantityPlacedTonnes: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 3)
    val quantityAlreadyFulfilledTonnes: BigDecimal,

    @Column(nullable = false)
    val recoveryTargetPercent: Int,

    @Column(nullable = false, precision = 12, scale = 3)
    val targetTonnes: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 3)
    val shortfallTonnes: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 3)
    val shortfallKg: BigDecimal,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Null when unauthenticated (calculator runs without login per PRD) */
    @Column(nullable = true)
    val userId: String? = null
)
