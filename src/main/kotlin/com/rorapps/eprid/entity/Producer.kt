package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.BatteryCategory
import com.rorapps.eprid.constants.WasteStreamType
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "producers", schema = "eprid")
data class Producer(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = true, unique = true)
    val cpcbRegNumber: String? = null,

    /** Stored as comma-separated enum names; parsed on read */
    @Column(name = "battery_categories", columnDefinition = "TEXT")
    private val batteryCategoriesRaw: String = "",

    /** Waste stream this producer was first created under. Informational, not a hard single-stream constraint. */
    @Enumerated(EnumType.STRING)
    @Column(name = "waste_stream", nullable = false)
    val wasteStream: WasteStreamType = WasteStreamType.BATTERY,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    val createdBy: User,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    // BatteryCategory split from 4 values to 7 (2026-07-10, real Schedule II fix) — this raw field is
    // otherwise unused by any DTO/service today, but parse defensively rather than throw on a legacy
    // stored name (e.g. old "PORTABLE"/"ELECTRIC_VEHICLE") in case any row predates the rename.
    @get:Transient
    val batteryCategories: List<BatteryCategory>
        get() = batteryCategoriesRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { name -> runCatching { BatteryCategory.valueOf(name) }.getOrNull() }
}
