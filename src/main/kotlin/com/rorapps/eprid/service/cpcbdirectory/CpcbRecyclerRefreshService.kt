package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.dto.cpcbdirectory.CpcbPendingReviewItemDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRecyclerScoreDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRecyclerSnapshotDiffDto
import com.rorapps.eprid.dto.cpcbdirectory.CpcbRefreshRunSummaryDto
import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.entity.CpcbRecyclerAuthorization
import com.rorapps.eprid.entity.CpcbRecyclerSnapshotDiff
import com.rorapps.eprid.entity.CpcbRefreshRun
import com.rorapps.eprid.entity.RefreshRunStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import com.rorapps.eprid.repository.CpcbRecyclerAuthorizationRepository
import com.rorapps.eprid.repository.CpcbRecyclerRepository
import com.rorapps.eprid.repository.CpcbRecyclerSnapshotDiffRepository
import com.rorapps.eprid.repository.CpcbRefreshRunRepository
import java.time.Instant

/**
 * Scheduled/manual refresh of the CPCB recycler directory (feature_spec_cpcb_directory_refresh.md).
 * Diff-and-flag, not silent-overwrite-and-serve: only tracked fields (§2) get a diff row, and a
 * risk-band flip gates behind [pendingReview] rather than silently changing what's shown externally
 * (§4) — same "no auto-apply" discipline already used by [CpcbRecyclerLinkService] for GST matching.
 */
@Service
class CpcbRecyclerRefreshService(
    private val fetcher: CpcbRecyclerJsonFetcher,
    private val recyclerRepository: CpcbRecyclerRepository,
    private val authorizationRepository: CpcbRecyclerAuthorizationRepository,
    private val scoringService: CpcbRecyclerScoringService,
    private val refreshRunRepository: CpcbRefreshRunRepository,
    private val diffRepository: CpcbRecyclerSnapshotDiffRepository,
    private val objectMapper: ObjectMapper,
    txManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** REQUIRES_NEW per row: one bad row (constraint violation, bad data) must not mark the
     *  whole-run transaction rollback-only and cascade-fail every row after it. Each row commits
     *  or rolls back on its own. */
    private val rowTransactionTemplate = TransactionTemplate(txManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    companion object {
        /** Fields that feed scoring (§2) — cosmetic fields (phone, email, address formatting) are
         *  deliberately excluded, they don't drive a diff row or a rescore. */
        val TRACKED_FIELDS: List<Pair<String, (CpcbRecycler) -> Any?>> = listOf(
            "recycler_consent_air" to { r: CpcbRecycler -> r.consentAirExpiry },
            "recycler_consent_water" to { r: CpcbRecycler -> r.consentWaterExpiry },
            "recycler_hwmd_valid" to { r: CpcbRecycler -> r.hwmdValidExpiry },
            "recycler_dic_valid" to { r: CpcbRecycler -> r.dicValidExpiry },
            "recycling_capacity" to { r: CpcbRecycler -> r.recyclingCapacity },
            "recycler_type" to { r: CpcbRecycler -> r.recyclerTypeRaw },
            "InspectionStatus" to { r: CpcbRecycler -> r.inspectionStatus },
            "InternalAppStatus" to { r: CpcbRecycler -> r.internalAppStatus },
            "certificate" to { r: CpcbRecycler -> r.certificateFlag }
        )

        /** Plain `==` on BigDecimal is scale-sensitive (6000.00 != 6000 by equals(), even though
         *  they're the same number) — recyclingCapacity is stored at DB precision(14,2) but a
         *  fresh JSON pull parses "6000" at scale 0, so a naive equals() flags a spurious diff on
         *  nearly every row. compareTo() is the numeric-equality check that actually matters here. */
        private fun valuesEqual(a: Any?, b: Any?): Boolean =
            if (a is java.math.BigDecimal && b is java.math.BigDecimal) a.compareTo(b) == 0 else a == b

        fun diffTrackedFields(old: CpcbRecycler, new: CpcbRecycler): List<Triple<String, String?, String?>> =
            TRACKED_FIELDS.mapNotNull { (name, extract) ->
                val oldVal = extract(old)
                val newVal = extract(new)
                if (valuesEqual(oldVal, newVal)) null else Triple(name, oldVal?.toString(), newVal?.toString())
            }
    }

    /** Runs every morning, before normal business hours — same pattern as
     *  eprid-nightly-product-doc-sync, shifted to AM (§6): the directory should reflect the latest
     *  pull before any founder/consultancy demo or outreach that day, not stale overnight data. */
    @Scheduled(cron = "0 30 4 * * *")
    fun scheduledRefresh() {
        runCatching { refresh() }.onFailure { log.error("Scheduled CPCB directory refresh failed", it) }
    }

    @Transactional
    fun refresh(): CpcbRefreshRunSummaryDto {
        val run = refreshRunRepository.save(CpcbRefreshRun(startedAt = Instant.now(), status = RefreshRunStatus.RUNNING))

        val rows = try {
            fetcher.fetchAll()
        } catch (ex: Exception) {
            log.error("CPCB refresh fetch failed", ex)
            return refreshRunRepository.save(
                run.copy(completedAt = Instant.now(), status = RefreshRunStatus.FAILED, errorDetail = ex.message?.take(2000))
            ).toDto()
        }

        var changed = 0
        var new = 0
        val errors = mutableListOf<String>()
        val seenCpcbIds = mutableSetOf<String>()

        for (row in rows) {
            try {
                row.cpcbId?.let { seenCpcbIds += it }
                var wasNew = false
                var wasChanged = false

                rowTransactionTemplate.execute {
                    val existing = row.cpcbId?.let { recyclerRepository.findByCpcbId(it) }
                    val mapped = CpcbRecyclerRowMapper.toEntity(row, existing)
                    val diffs = if (existing != null) diffTrackedFields(existing, mapped.entity) else emptyList()

                    val saved = recyclerRepository.save(
                        mapped.entity.copy(lastSyncedAt = Instant.now(), noLongerListedAt = null)
                    )

                    authorizationRepository.deleteAllByRecyclerId(saved.id!!)
                    CpcbRecyclerCsvParser.parseAuthorizations(row.recyclerTypeRaw).forEach { auth ->
                        authorizationRepository.save(
                            CpcbRecyclerAuthorization(recycler = saved, categoryCode = auth.categoryCode, categoryLabel = auth.categoryLabel)
                        )
                    }

                    if (existing == null) {
                        wasNew = true
                    } else if (diffs.isNotEmpty()) {
                        wasChanged = true
                        diffs.forEach { (field, oldVal, newVal) ->
                            diffRepository.save(
                                CpcbRecyclerSnapshotDiff(
                                    recyclerId = saved.id!!, refreshRunId = run.id!!,
                                    fieldName = field, oldValue = oldVal, newValue = newVal
                                )
                            )
                        }
                    }

                    // Sub-score movement that doesn't cross a band boundary applies automatically;
                    // a band flip gates behind pendingReview (§4) — only recompute when something
                    // tracked actually changed, so an unchanged run touches no recycler's score.
                    if (existing == null || diffs.isNotEmpty()) {
                        val previousBand = scoringService.latestScore(saved.id!!)?.riskBand
                        val newScore = scoringService.scoreAndSave(saved)
                        if (previousBand != null && previousBand != newScore.riskBand) {
                            recyclerRepository.save(saved.copy(pendingReview = true))
                        }
                    }
                }

                if (wasNew) new++
                if (wasChanged) changed++
            } catch (e: Exception) {
                log.error("CPCB refresh failed for row '${row.recyclerName}'", e)
                errors += "${row.recyclerName}: ${e.message}"
            }
        }

        val missingCount = flagMissingRecyclers(seenCpcbIds)

        return refreshRunRepository.save(
            run.copy(
                completedAt = Instant.now(),
                recordsFetched = rows.size,
                recordsChanged = changed,
                recordsNew = new,
                recordsMissing = missingCount,
                status = if (errors.isEmpty()) RefreshRunStatus.SUCCESS else RefreshRunStatus.PARTIAL,
                errorDetail = errors.ifEmpty { null }?.joinToString(" | ")?.take(4000)
            )
        ).toDto()
    }

    /** Previously-seen id now missing from the pull → flagged, never deleted (§3: could be
     *  deregistration, could be a CPCB-side pagination hiccup — a flag is reversible, a delete isn't). */
    private fun flagMissingRecyclers(seenCpcbIds: Set<String>): Int {
        val nowMissing = recyclerRepository.findAllByCpcbIdIsNotNull()
            .filter { it.cpcbId !in seenCpcbIds && it.noLongerListedAt == null }
        nowMissing.forEach { recyclerRepository.save(it.copy(noLongerListedAt = Instant.now())) }
        return nowMissing.size
    }

    @Transactional(readOnly = true)
    fun recentRuns(limit: Int = 30) =
        refreshRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit)).map { it.toDto() }

    @Transactional(readOnly = true)
    fun pendingReview(): List<CpcbPendingReviewItemDto> =
        recyclerRepository.findAllByPendingReviewTrue().map { recycler ->
            val latest = scoringService.latestScore(recycler.id!!)?.let { score ->
                CpcbRecyclerScoreDto(
                    compositeScore = score.compositeScore,
                    riskBand = score.riskBand,
                    flags = objectMapper.readValue(score.flags, List::class.java).map { it.toString() },
                    unassessed = objectMapper.readValue(score.unassessed, List::class.java).map { it.toString() },
                    layerBreakdown = objectMapper.readValue(score.layerBreakdown, Map::class.java)
                        .entries.associate { it.key.toString() to it.value },
                    scoreConfidence = score.scoreConfidence,
                    scoredAt = score.scoredAt.toString()
                )
            }
            CpcbPendingReviewItemDto(
                id = recycler.id,
                cpcbId = recycler.cpcbId,
                recyclerName = recycler.recyclerName,
                latestScore = latest,
                recentDiffs = diffRepository.findAllByRecyclerIdOrderByDetectedAtDesc(recycler.id).take(20).map {
                    CpcbRecyclerSnapshotDiffDto(it.fieldName, it.oldValue, it.newValue, it.detectedAt)
                }
            )
        }

    @Transactional
    fun confirmReview(recyclerId: String) {
        val recycler = recyclerRepository.findById(recyclerId).orElseThrow {
            NoSuchElementException("CPCB directory recycler not found: $recyclerId")
        }
        recyclerRepository.save(recycler.copy(pendingReview = false))
    }
}

private fun CpcbRefreshRun.toDto() = CpcbRefreshRunSummaryDto(
    id = id!!,
    startedAt = startedAt,
    completedAt = completedAt,
    recordsFetched = recordsFetched,
    recordsChanged = recordsChanged,
    recordsNew = recordsNew,
    recordsMissing = recordsMissing,
    status = status,
    errorDetail = errorDetail
)
