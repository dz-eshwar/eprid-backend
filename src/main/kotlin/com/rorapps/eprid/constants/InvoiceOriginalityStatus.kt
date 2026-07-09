package com.rorapps.eprid.constants

/**
 * Result of the e-invoice QR originality check (PRD §7.1, Tier 1).
 * Deliberately 4-state, not pass/fail: NOT_APPLICABLE (no QR expected) must stay
 * distinguishable from COULD_NOT_VERIFY (QR present but unverifiable) so a recycler
 * below the e-invoicing turnover threshold is never flagged as suspicious.
 */
enum class InvoiceOriginalityStatus { VALID, INVALID, COULD_NOT_VERIFY, NOT_APPLICABLE }
