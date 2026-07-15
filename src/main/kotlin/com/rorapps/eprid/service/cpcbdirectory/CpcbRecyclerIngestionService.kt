package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.dto.cpcbdirectory.CpcbIngestionSummaryDto
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

    @Transactional
    fun ingest(reader: Reader): CpcbIngestionSummaryDto {
        val rows = CpcbRecyclerCsvParser.parse(reader)
        var upserted = 0
        var partialCaptureCount = 0
        var missingSourceIdCount = 0
        val errors = mutableListOf<String>()

        for (row in rows) {
            try {
                if (row.cpcbId == null) missingSourceIdCount++

                val existing = row.cpcbId?.let { recyclerRepository.findByCpcbId(it) }
                val mapped = CpcbRecyclerRowMapper.toEntity(row, existing)
                if (mapped.isPartialCapture) partialCaptureCount++

                val saved = recyclerRepository.save(mapped.entity)

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
