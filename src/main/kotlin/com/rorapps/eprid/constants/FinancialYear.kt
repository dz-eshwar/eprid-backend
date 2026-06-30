package com.rorapps.eprid.constants

/**
 * Supported financial years for EPR obligation calculations.
 * Add new entries here when CPCB publishes updated Schedule II targets.
 */
enum class FinancialYear(val label: String) {
    FY_2024_25("2024-25"),
    FY_2025_26("2025-26"),
    FY_2026_27("2026-27");

    companion object {
        fun fromLabel(label: String): FinancialYear =
            entries.firstOrNull { it.label == label }
                ?: throw IllegalArgumentException("Unsupported financial year: $label")
    }
}
