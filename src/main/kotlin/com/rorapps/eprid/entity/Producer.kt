package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.BatteryCategory
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    val createdBy: User,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    @get:Transient
    val batteryCategories: List<BatteryCategory>
        get() = batteryCategoriesRaw
            .split(",")
            .filter { it.isNotBlank() }
            .map { BatteryCategory.valueOf(it.trim()) }
}
