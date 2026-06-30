package com.rorapps.eprid.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "regulatory_findings", schema = "eprid")
data class RegulatoryFinding(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycler_id", nullable = false)
    val recycler: Recycler,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = true)
    val check: VerificationCheck? = null,

    /** CPCB | NGT | SPCB | NEWS | CLAUDE_ANALYSIS */
    @Column(nullable = false)
    val source: String,

    /** ENFORCEMENT_NOTICE | COURT_ORDER | SUSPENSION | NEWS_MENTION | NO_RECORD */
    @Column(nullable = false)
    val findingType: String,

    /** HIGH | MEDIUM | LOW | INFO */
    @Column(nullable = false)
    val severity: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val summary: String,

    @Column(nullable = true, columnDefinition = "TEXT")
    val url: String? = null,

    @Column(nullable = true)
    val findingDate: LocalDate? = null,

    /** HIGH | MEDIUM | LOW — how confident Claude is in this finding */
    @Column(nullable = false)
    val confidence: String = "MEDIUM",

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
