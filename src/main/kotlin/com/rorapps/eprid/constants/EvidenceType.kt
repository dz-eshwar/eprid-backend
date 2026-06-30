package com.rorapps.eprid.constants

enum class EvidenceType(
    val label: String,
    val description: String
) {
    SITE_PHOTO(
        label = "Site photo",
        description = "Photo taken at the recycling facility during or around processing"
    ),
    WEIGHBRIDGE_SLIP(
        label = "Weighbridge slip",
        description = "Official weight measurement record from the processing date"
    ),
    INVOICE(
        label = "Invoice",
        description = "Commercial invoice related to the batch — may precede or follow processing by up to 30 days"
    ),
    REGISTRATION_CERTIFICATE(
        label = "Registration certificate",
        description = "CPCB / SPCB registration or authorization — expected to predate the processing date"
    ),
    AUDIT_REPORT(
        label = "Audit report",
        description = "Third-party or internal audit report — expected within the same financial year"
    ),
    OTHER(
        label = "Other",
        description = "Any other supporting document — default ±30 day tolerance applied"
    )
}
