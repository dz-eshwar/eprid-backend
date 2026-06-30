package com.rorapps.eprid.entity

import jakarta.persistence.*
import java.time.Instant

enum class VaultDocType {
    // Identity & Registration
    REGISTRATION_CERT,              // BWMR recycler registration certificate
    GST_CERT,                       // GST registration certificate
    PAN_CARD,                       // PAN card
    INCORPORATION_CERT,             // Company incorporation / partnership deed

    // Regulatory Authorizations
    PCB_AUTHORIZATION,              // State/Central PCB consent to operate
    HAZARDOUS_WASTE_AUTHORIZATION,  // Authorization under HWM Rules 2016
    EPR_REGISTRATION_CERT,          // EPR registration from CPCB portal

    // Capacity & Infrastructure
    CAPACITY_CERTIFICATE,           // Annual recycling capacity (tonnes/yr) from competent authority

    // Processing Evidence — primary source for compliance form autofill
    PROCESSING_RECEIPT,             // Recycling completion receipt per batch
    WEIGHBRIDGE_SLIP,               // Weight slip proving quantity received/processed
    GATE_PASS,                      // Inward receipt when batteries arrive at facility
    CONSIGNMENT_NOTE,               // E-way bill / transport document
    CERTIFICATE_OF_RECYCLING,       // Certificate issued to producer proving recycling done

    // Compliance Reports
    ANNUAL_RETURN,                  // Filed annual compliance return
    QUARTERLY_REPORT,               // Quarterly progress report to CPCB/SPCB
    THIRD_PARTY_AUDIT_REPORT,       // Third-party audit report

    OTHER
}

@Entity
@Table(name = "vault_documents", schema = "eprid")
data class VaultDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycler_id", nullable = false)
    val recycler: Recycler,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val uploadedBy: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val docType: VaultDocType,

    @Column(nullable = false)
    val displayName: String,

    @Column(nullable = false)
    val fileName: String,

    @Column(nullable = false)
    val contentType: String,

    @Column(nullable = false)
    val fileSizeBytes: Long,

    @Column(name = "s3_key", nullable = false)
    val s3Key: String,

    /** Must be set before any upload — logged for audit trail */
    @Column(nullable = false)
    val consentAcceptedAt: Instant,

    @Column(nullable = true, columnDefinition = "TEXT")
    val notes: String? = null,

    @Column(nullable = false, updatable = false)
    val uploadedAt: Instant = Instant.now(),

    /** Soft-delete — null means active */
    @Column(nullable = true)
    val deletedAt: Instant? = null
)
