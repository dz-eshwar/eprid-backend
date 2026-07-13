package com.rorapps.eprid.dto.calculator

import com.rorapps.eprid.constants.BatteryCategory
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class ComplianceEstimateRequest(
    @field:NotNull(message = "Battery category is required")
    val batteryCategory: BatteryCategory,

    @field:NotNull(message = "Financial year is required")
    val financialYear: String,

    /** Schedule II's collection target technically applies to a *prior reference year's* quantity
     *  placed in market (see RecoveryTargets.kt), not this FY's — this calculator has no producer
     *  history store for prior-year figures, so this value is used as a same-year proxy. The response
     *  states which reference year the % nominally applies to so this approximation is visible, not silent. */
    @field:NotNull(message = "Quantity placed in market is required")
    @field:DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    val quantityPlacedTonnes: BigDecimal,

    /** Tonnes already collected/recycled or certificates already held — optional */
    @field:DecimalMin(value = "0.0", message = "Quantity fulfilled cannot be negative")
    val quantityAlreadyFulfilledTonnes: BigDecimal? = null
)
