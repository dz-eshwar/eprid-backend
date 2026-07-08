package com.rorapps.eprid.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * CPCB's own public battery-recycler directory — a separate concept from [Recycler], which holds
 * producer-created/user-upserted recyclers tied to a [VerificationCheck]. This is the raw ingested
 * registry, scoped to "Entity Health Score" only (registration/authorization/geography) — see
 * product_document_built_state.md for why certificate-volume/invoice fields are deliberately absent.
 */
@Entity
@Table(name = "cpcb_recyclers", schema = "eprid")
data class CpcbRecycler(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    /** CPCB's own row id, as a string — nullable: some captured rows have no source id at all
     *  (a partial-capture artifact, not evidence the recycler itself lacks one). */
    @Column(name = "cpcb_id", nullable = true)
    val cpcbId: String? = null,

    @Column(name = "cpcb_uuid", nullable = true)
    val cpcbUuid: String? = null,

    @Column(name = "recycler_name", nullable = false)
    val recyclerName: String,

    @Column(name = "recycler_address", nullable = true)
    val recyclerAddress: String? = null,

    @Column(name = "state_id", nullable = true)
    val stateId: String? = null,

    @Column(name = "recycler_gst_no", nullable = true)
    val recyclerGstNo: String? = null,

    @Column(name = "consent_air_expiry", nullable = true)
    val consentAirExpiry: LocalDate? = null,

    @Column(name = "consent_water_expiry", nullable = true)
    val consentWaterExpiry: LocalDate? = null,

    @Column(name = "hwmd_valid_expiry", nullable = true)
    val hwmdValidExpiry: LocalDate? = null,

    @Column(name = "dic_valid_expiry", nullable = true)
    val dicValidExpiry: LocalDate? = null,

    @Column(name = "recycler_type_raw", nullable = true, columnDefinition = "TEXT")
    val recyclerTypeRaw: String? = null,

    /** Units NOT confirmed against CPCB documentation — likely MT/year, unverified. Any threshold
     *  compared against this column is a raw-number comparison, not unit-aware. */
    @Column(name = "recycling_capacity", nullable = true, precision = 14, scale = 2)
    val recyclingCapacity: BigDecimal? = null,

    @Column(nullable = true, precision = 10, scale = 6)
    val latitude: BigDecimal? = null,

    @Column(nullable = true, precision = 10, scale = 6)
    val longitude: BigDecimal? = null,

    /** PII of an individual, not the company — never surfaced in customer-facing responses by
     *  default. See [com.rorapps.eprid.service.cpcbdirectory.CpcbRecyclerSearchService]. */
    @Column(name = "authorized_name", nullable = true)
    val authorizedName: String? = null,

    @Column(name = "authorized_email", nullable = true)
    val authorizedEmail: String? = null,

    @Column(name = "authorized_mobile", nullable = true)
    val authorizedMobile: String? = null,

    @Column(name = "source_created_at", nullable = true)
    val sourceCreatedAt: Instant? = null,

    @Column(name = "certificate_flag", nullable = true)
    val certificateFlag: String? = null,

    /** NULL = not captured. 0 = confirmed zero. Do not conflate the two. */
    @Column(name = "staff_no", nullable = true)
    val staffNo: Int? = null,

    @Column(name = "worker_no", nullable = true)
    val workerNo: Int? = null,

    /** CPCB's own internal status code — meaning not decoded, stored raw only, not scored on. */
    @Column(name = "inspection_status", nullable = true)
    val inspectionStatus: Int? = null,

    @Column(name = "internal_app_status", nullable = true)
    val internalAppStatus: Int? = null,

    @Column(name = "recycler_web_address", nullable = true)
    val recyclerWebAddress: String? = null,

    @Column(name = "recycler_phone_no", nullable = true)
    val recyclerPhoneNo: String? = null,

    /** PII of an individual, same treatment as authorizedMobile — never surfaced by default. */
    @Column(name = "authorized_phone", nullable = true)
    val authorizedPhone: String? = null,

    @Column(name = "installed_date", nullable = true)
    val installedDate: LocalDate? = null,

    @Column(name = "operating_date", nullable = true)
    val operatingDate: LocalDate? = null,

    /** NULL = not captured (100% of the 2026-07-08 pull) — not scored on until CPCB actually
     *  populates these; see CpcbRecyclerScoring's isoBothOnFile wiring. */
    @Column(name = "iso_9001_upload", nullable = true)
    val iso9001Upload: Boolean? = null,

    @Column(name = "iso_14001_upload", nullable = true)
    val iso14001Upload: Boolean? = null,

    @Column(name = "apcm_upload", nullable = true)
    val apcmUpload: Boolean? = null,

    @Column(name = "wpcm_upload", nullable = true)
    val wpcmUpload: Boolean? = null,

    /** CPCB's own internal status codes — meaning not decoded, stored raw only, not scored on. */
    @Column(name = "application_status", nullable = true)
    val applicationStatus: Int? = null,

    @Column(name = "payment_status", nullable = true)
    val paymentStatus: Int? = null,

    @Column(name = "certificate_no", nullable = true)
    val certificateNo: String? = null,

    @Column(name = "certificate_date", nullable = true)
    val certificateDate: LocalDate? = null,

    /** CPCB's own updated_at for this row, if captured — distinct from our own [updatedAt]. */
    @Column(name = "source_updated_at", nullable = true)
    val sourceUpdatedAt: Instant? = null,

    @Column(name = "mrai_memb", nullable = true)
    val mraiMemb: Boolean? = null,

    @Column(name = "sop_recycling", nullable = true)
    val sopRecycling: Boolean? = null,

    @Column(name = "esg_policy", nullable = true)
    val esgPolicy: Boolean? = null,

    @Column(name = "website_link", nullable = true)
    val websiteLink: String? = null,

    /** True when this row is missing enough optional fields that a blank shouldn't read as
     *  "confirmed absent" — see CpcbRecyclerIngestionService.isPartialCapture for the heuristic. */
    @Column(name = "data_quality_partial_capture", nullable = false)
    val dataQualityPartialCapture: Boolean = false,

    @Column(name = "data_quality_notes", nullable = true, columnDefinition = "TEXT")
    val dataQualityNotes: String? = null,

    @Column(name = "ingested_at", nullable = false)
    val ingestedAt: Instant = Instant.now(),

    @Column(nullable = false)
    val source: String = "cpcb_battery_recyclerview",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "cpcb_recycler_authorizations", schema = "eprid")
data class CpcbRecyclerAuthorization(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycler_id", nullable = false)
    val recycler: CpcbRecycler,

    /** e.g. "R1".."R4" — "UNKNOWN" if the source string didn't match the expected "Rn: ..." shape. */
    @Column(name = "category_code", nullable = false)
    val categoryCode: String,

    @Column(name = "category_label", nullable = false, columnDefinition = "TEXT")
    val categoryLabel: String
)

/** 'entity_health' today; 'certificate_risk' reserved for when Layer 2 (yield) / Layer 4
 *  (invoice traceability) data actually exists. Don't claim certificate_risk before then. */
enum class ScoreConfidence { ENTITY_HEALTH, CERTIFICATE_RISK }

/** History preserved (one row per scoring run) — same convention as [RecyclerCredentialCheck].
 *  Query latest by recyclerId ORDER BY scoredAt DESC rather than overwriting in place. */
@Entity
@Table(name = "cpcb_recycler_scores", schema = "eprid")
data class CpcbRecyclerScore(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycler_id", nullable = false)
    val recycler: CpcbRecycler,

    @Column(name = "composite_score", nullable = false)
    val compositeScore: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_band", nullable = false)
    val riskBand: RiskRating,

    /** JSON array of human-readable confirmed-issue strings. */
    @Column(nullable = false, columnDefinition = "TEXT")
    val flags: String,

    /** JSON array of signals that couldn't be checked (missing/partial data) — distinct from a
     *  clean pass. A recycler with 0 flags and several unassessed signals is not the same finding
     *  as a recycler with 0 flags and everything checked. */
    @Column(nullable = false, columnDefinition = "TEXT")
    val unassessed: String,

    /** JSON object: per-layer points + reasoning. JSON (not columns) so Layer 2/4 fields can be
     *  added later without a migration. */
    @Column(name = "layer_breakdown", nullable = false, columnDefinition = "TEXT")
    val layerBreakdown: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "score_confidence", nullable = false)
    val scoreConfidence: ScoreConfidence = ScoreConfidence.ENTITY_HEALTH,

    @Column(name = "scored_at", nullable = false)
    val scoredAt: Instant = Instant.now()
)

/** Static, editable geographic risk reference (battery-specific, from E-PRid's risk-methodology
 *  research — not derived from the CPCB feed itself). Point+radius is a coarse district proxy, not
 *  real boundary data. */
@Entity
@Table(name = "cpcb_geo_risk_hotspots", schema = "eprid")
data class CpcbGeoRiskHotspot(
    @Id
    val id: String,

    @Column(name = "location_name", nullable = false)
    val locationName: String,

    @Column(name = "risk_level", nullable = false)
    val riskLevel: String,

    @Column(nullable = false)
    val points: Int,

    @Column(nullable = false, precision = 10, scale = 6)
    val latitude: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 6)
    val longitude: BigDecimal,

    @Column(name = "radius_km", nullable = false, precision = 6, scale = 2)
    val radiusKm: BigDecimal
)
