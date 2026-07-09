package com.rorapps.eprid.constants

/**
 * The six government-authorized Invoice Registration Portals (IRPs) that can issue
 * signed e-invoice QR codes. `trusted = false` means we do not have a reliably fetchable
 * public key for that IRP yet (PRD §7.1) — any invoice from an untrusted IRP must fall
 * through to COULD_NOT_VERIFY, never a guess.
 */
enum class EInvoiceIrp(val label: String, val trusted: Boolean) {
    IRP1_NIC("NIC", trusted = true),
    IRP2_CLEARTAX("Cleartax", trusted = true),
    IRP3_CYGNET("Cygnet", trusted = false),
    IRP4_CLEAR("Clear", trusted = true),
    IRP5_EY("Ernst & Young", trusted = true),
    IRP6_IRIS("IRIS", trusted = false);

    companion object {
        val TRUSTED = entries.filter { it.trusted }
    }
}
