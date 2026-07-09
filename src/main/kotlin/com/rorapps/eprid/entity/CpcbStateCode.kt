package com.rorapps.eprid.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Maps CPCB's own internal `cpcb_recyclers.state_id` numeric codes to real state names.
 *
 * Not CPCB-published — no such table exists at source (confirmed: these values don't match
 * standard GST state codes either, e.g. state_id 7 here is Himachal Pradesh, but GST code 07
 * is Delhi). Inferred by cross-referencing each state_id against the district/industrial-area
 * names in that code's recycler_address values across the 553-row 2026-07-08 pull — same
 * "static, editable reference" convention as [CpcbGeoRiskHotspot]. Only covers state_ids
 * actually observed in that pull; update if a future re-pull surfaces a new one.
 */
@Entity
@Table(name = "cpcb_state_codes", schema = "eprid")
data class CpcbStateCode(
    @Id
    @Column(name = "state_id")
    val stateId: String,

    @Column(name = "state_name", nullable = false)
    val stateName: String
)
