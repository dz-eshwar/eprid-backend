package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.dto.cpcbdirectory.CpcbIngestionSummaryDto
import com.rorapps.eprid.entity.CpcbRecycler
import com.rorapps.eprid.entity.CpcbRecyclerAuthorization
import com.rorapps.eprid.repository.CpcbRecyclerAuthorizationRepository
import com.rorapps.eprid.repository.CpcbRecyclerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.Reader

@Service
class CpcbRecyclerIngestionService(
    private val recyclerRepository: CpcbRecyclerRepository,
    private val authorizationRepository: CpcbRecyclerAuthorizationRepository,
    private val scoringService: CpcbRecyclerScoringService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * Fields whose combined blankness signals "this row wasn't fully captured in this query
         * pass" rather than "these fields are confirmed absent at CPCB." Threshold picked against
         * the seed data: normal rows (even ones with real expired dates) have 0-3 blanks among
         * these 10; rows like a large player captured without registration/geo data in this pass
         * have 6+. Heuristic, not a CPCB-documented signal — revisit if a full re-pull shows a
         * different pattern.
         */
        const val PARTIAL_CAPTURE_BLANK_THRESHOLD = 6

        fun blankFieldCount(row: ParsedRecyclerRow): Int = listOf(
            row.recyclerGstNo, row.consentAirExpiry, row.consentWaterExpiry, row.hwmdValidExpiry,
            row.recyclerTypeRaw, row.recyclingCapacity, row.latitude, row.longitude,
            row.staffNo, row.workerNo
        ).count { it == null }

        fun isPartialCapture(row: ParsedRecyclerRow): Boolean =
            blankFieldCount(row) >= PARTIAL_CAPTURE_BLANK_THRESHOLD
    }

    @Transactional
    fun ingest(reader: Reader): CpcbIngestionSummaryDto {
        val rows = CpcbRecyclerCsvParser.parse(reader)
        var upserted = 0
        var partialCaptureCount = 0
        var missingSourceIdCount = 0
        val errors = mutableListOf<String>()

        for (row in rows) {
            try {
                val blankCount = blankFieldCount(row)
                val isPartialCapture = isPartialCapture(row)

                if (row.cpcbId == null) missingSourceIdCount++
                if (isPartialCapture) partialCaptureCount++

                val notes = buildList {
                    if (row.cpcbId == null) add(
                        "missing_source_id: CPCB row id/uuid not captured — likely a partial-capture " +
                            "artifact, not confirmed to be absent at source"
                    )
                    if (isPartialCapture) add(
                        "partial_capture: $blankCount of 10 optional fields blank in this row — treat " +
                            "blanks here as unassessed, not confirmed-missing (see CpcbRecyclerIngestionService)"
                    )
                }.ifEmpty { null }?.joinToString(" | ")

                val existing = row.cpcbId?.let { recyclerRepository.findByCpcbId(it) }

                val entity = (existing ?: CpcbRecycler(recyclerName = row.recyclerName)).copy(
                    cpcbId = row.cpcbId,
                    cpcbUuid = row.cpcbUuid,
                    recyclerName = row.recyclerName,
                    recyclerAddress = row.recyclerAddress,
                    stateId = row.stateId,
                    recyclerGstNo = row.recyclerGstNo,
                    consentAirExpiry = row.consentAirExpiry,
                    consentWaterExpiry = row.consentWaterExpiry,
                    hwmdValidExpiry = row.hwmdValidExpiry,
                    dicValidExpiry = row.dicValidExpiry,
                    recyclerTypeRaw = row.recyclerTypeRaw,
                    recyclingCapacity = row.recyclingCapacity,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    authorizedName = row.authorizedName,
                    authorizedEmail = row.authorizedEmail,
                    authorizedMobile = row.authorizedMobile,
                    sourceCreatedAt = row.sourceCreatedAt,
                    certificateFlag = row.certificateFlag,
                    staffNo = row.staffNo,
                    workerNo = row.workerNo,
                    inspectionStatus = row.inspectionStatus,
                    internalAppStatus = row.internalAppStatus,
                    dataQualityPartialCapture = isPartialCapture,
                    dataQualityNotes = notes,
                    updatedAt = java.time.Instant.now()
                )
                val saved = recyclerRepository.save(entity)

                authorizationRepository.deleteAllByRecyclerId(saved.id!!)
                CpcbRecyclerCsvParser.parseAuthorizations(row.recyclerTypeRaw).forEach { auth ->
                    authorizationRepository.save(
                        CpcbRecyclerAuthorization(
                            recycler = saved,
                            categoryCode = auth.categoryCode,
                            categoryLabel = auth.categoryLabel
                        )
                    )
                }

                scoringService.scoreAndSave(saved)
                upserted++
            } catch (e: Exception) {
                log.error("Failed to ingest row for '${row.recyclerName}'", e)
                errors.add("${row.recyclerName}: ${e.message}")
            }
        }

        return CpcbIngestionSummaryDto(
            rowsRead = rows.size,
            rowsUpserted = upserted,
            rowsFlaggedPartialCapture = partialCaptureCount,
            rowsMissingSourceId = missingSourceIdCount,
            errors = errors
        )
    }
}
