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

    @field:NotNull(message = "Quantity placed in market is required")
    @field:DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    val quantityPlacedTonnes: BigDecimal,

    /** Tonnes already collected/recycled or certificates already held — optional */
    @field:DecimalMin(value = "0.0", message = "Quantity fulfilled cannot be negative")
    val quantityAlreadyFulfilledTonnes: BigDecimal? = null
)
