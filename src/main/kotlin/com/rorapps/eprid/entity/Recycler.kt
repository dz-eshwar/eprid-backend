package com.rorapps.eprid.entity

import com.rorapps.eprid.constants.WasteStreamType
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "recyclers", schema = "eprid")
data class Recycler(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = true, unique = true)
    val bwmrRegNumber: String? = null,

    /** Self-reported annual processing capacity in tonnes */
    @Column(name = "self_reported_capacity_t", nullable = true, precision = 12, scale = 3)
    val selfReportedCapacityT: BigDecimal? = null,

    @Column(nullable = true)
    val state: String? = null,

    /** Waste stream this recycler was first created under. Informational, not a hard single-stream constraint —
     *  a recycler processing multiple streams is plausible; each Check's own wasteStream is authoritative. */
    @Enumerated(EnumType.STRING)
    @Column(name = "waste_stream", nullable = false)
    val wasteStream: WasteStreamType = WasteStreamType.BATTERY,

    /** Set when this recycler was created via RECYCLER user registration — null for recyclers upserted via Module A checks */
    @Column(name = "user_id", nullable = true, unique = true)
    val userId: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    val updatedAt: Instant = Instant.now()
)
