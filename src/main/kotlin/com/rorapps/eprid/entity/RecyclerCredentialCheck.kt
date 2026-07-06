package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.CredentialCheckResult
import com.rorapps.eprid.constants.CredentialCheckType
import jakarta.persistence.*
import java.time.Instant

/** One row per credential-check attempt (history preserved, not overwritten on re-check). */
@Entity
@Table(name = "recycler_credential_checks", schema = "eprid")
data class RecyclerCredentialCheck(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycler_id", nullable = false)
    val recycler: Recycler,

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    val checkType: CredentialCheckType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val result: CredentialCheckResult,

    @Column(nullable = false)
    val provider: String,

    @Column(nullable = true, columnDefinition = "TEXT")
    val reason: String? = null,

    @Column(name = "checked_at", nullable = false)
    val checkedAt: Instant = Instant.now()
)
