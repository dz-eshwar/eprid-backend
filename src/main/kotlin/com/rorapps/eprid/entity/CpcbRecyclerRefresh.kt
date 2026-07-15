package com.rorapps.eprid.entity

import jakarta.persistence.*
import java.time.Instant

enum class RefreshRunStatus { RUNNING, SUCCESS, PARTIAL, FAILED }

/** One row per scheduled/manual CPCB directory refresh pull (feature_spec_cpcb_directory_refresh.md
 *  §2/§3). PARTIAL means some rows fetched but some failed to upsert — not a fetch failure. */
@Entity
@Table(name = "cpcb_refresh_run", schema = "eprid")
data class CpcbRefreshRun(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "completed_at", nullable = true)
    val completedAt: Instant? = null,

    @Column(name = "records_fetched", nullable = false)
    val recordsFetched: Int = 0,

    @Column(name = "records_changed", nullable = false)
    val recordsChanged: Int = 0,

    @Column(name = "records_new", nullable = false)
    val recordsNew: Int = 0,

    @Column(name = "records_missing", nullable = false)
    val recordsMissing: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RefreshRunStatus = RefreshRunStatus.RUNNING,

    @Column(name = "error_detail", nullable = true, columnDefinition = "TEXT")
    val errorDetail: String? = null
)

/** One row per changed tracked field per recycler per run (feature_spec_cpcb_directory_refresh.md
 *  §2) — no row is written for an unchanged recycler or an untracked/cosmetic field. Plain FK
 *  columns rather than JPA relations, matching the convention already used for
 *  claimed_metal_recoveries/metal_composition_checks (V19). */
@Entity
@Table(name = "cpcb_recycler_snapshot_diff", schema = "eprid")
data class CpcbRecyclerSnapshotDiff(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(name = "recycler_id", nullable = false)
    val recyclerId: String,

    @Column(name = "refresh_run_id", nullable = false)
    val refreshRunId: String,

    @Column(name = "field_name", nullable = false)
    val fieldName: String,

    @Column(name = "old_value", nullable = true, columnDefinition = "TEXT")
    val oldValue: String? = null,

    @Column(name = "new_value", nullable = true, columnDefinition = "TEXT")
    val newValue: String? = null,

    @Column(name = "detected_at", nullable = false)
    val detectedAt: Instant = Instant.now()
)
