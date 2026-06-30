package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.service.forensics.StateMatchStatus
import jakarta.persistence.*
import java.time.Instant

enum class ForensicsStatus { PENDING, PASS, FAIL, UNVERIFIABLE }

@Entity
@Table(name = "evidence", schema = "eprid")
data class Evidence(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    val check: VerificationCheck,

    @Column(nullable = false)
    val fileName: String,

    @Column(nullable = false)
    val contentType: String,

    @Column(nullable = false)
    val fileSizeBytes: Long,

    @Column(nullable = false)
    val storagePath: String,

    // ─── EXIF fields ──────────────────────────────────────────────────────────
    val exifLatitude: Double? = null,
    val exifLongitude: Double? = null,
    val exifDatetime: Instant? = null,
    val exifDevice: String? = null,

    // ─── PDF metadata fields ──────────────────────────────────────────────────
    val pdfAuthor: String? = null,
    val pdfCreator: String? = null,
    val pdfCreatedAt: Instant? = null,
    val pdfModifiedAt: Instant? = null,

    /** 64-bit perceptual hash hex string for duplicate image detection */
    val imagePhash: String? = null,

    // ─── Fix 1: document type (determines date tolerance applied) ─────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val evidenceType: EvidenceType = EvidenceType.OTHER,

    // ─── Fix 2: reverse-geocoded state and match result ───────────────────────
    val resolvedState: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    val stateMatch: StateMatchStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val forensicsStatus: ForensicsStatus = ForensicsStatus.PENDING,

    @Column(nullable = true, columnDefinition = "TEXT")
    val forensicsNotes: String? = null,

    @Column(nullable = false, updatable = false)
    val uploadedAt: Instant = Instant.now()
)
