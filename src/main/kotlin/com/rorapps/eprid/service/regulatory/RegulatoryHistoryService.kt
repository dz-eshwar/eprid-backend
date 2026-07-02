package com.rorapps.eprid.service.regulatory

import com.rorapps.eprid.dto.regulatory.RegulatoryFindingDto
import com.rorapps.eprid.dto.regulatory.RegulatoryHistoryResponse
import com.rorapps.eprid.entity.RegulatoryFinding
import com.rorapps.eprid.entity.RegulatoryStatus
import com.rorapps.eprid.entity.VerificationCheck
import com.rorapps.eprid.repository.RegulatoryFindingRepository
import com.rorapps.eprid.repository.VerificationCheckRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegulatoryHistoryService(
    private val claudeApiClient: ClaudeApiClient,
    private val findingRepository: RegulatoryFindingRepository,
    private val checkRepository: VerificationCheckRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Kicks off regulatory research in a background thread.
     * The check's regulatoryStatus transitions: NOT_STARTED → PENDING → COMPLETE | FAILED.
     */
    @Async
    @Transactional
    fun triggerAsync(check: VerificationCheck) {
        val checkId    = check.id!!
        val recycler   = check.recycler

        log.info("Starting regulatory history research for check $checkId (recycler: ${recycler.name})")

        // Mark as pending
        checkRepository.save(check.copy(regulatoryStatus = RegulatoryStatus.PENDING))

        val analysis = claudeApiClient.analyseRegulatoryHistory(
            recyclerName   = recycler.name,
            bwmrRegNumber  = recycler.bwmrRegNumber,
            state          = recycler.state
        )

        if (analysis == null) {
            // API not configured or call failed — store a single informational finding
            checkRepository.save(
                check.copy(
                    regulatoryStatus  = RegulatoryStatus.FAILED,
                    regulatoryRisk    = "UNKNOWN",
                    regulatorySummary = "Regulatory research could not be completed. " +
                                        "ANTHROPIC_API_KEY may not be configured or the API call failed."
                )
            )
            findingRepository.save(
                RegulatoryFinding(
                    recycler    = recycler,
                    check       = check,
                    source      = "CLAUDE_ANALYSIS",
                    findingType = "NO_RECORD",
                    severity    = "INFO",
                    title       = "Regulatory research unavailable",
                    summary     = "The AI regulatory research service is not available. Configure " +
                                  "ANTHROPIC_API_KEY to enable automatic regulatory history lookups.",
                    confidence  = "HIGH"
                )
            )
            return
        }

        // Persist each finding
        val entities = analysis.findings.map { f ->
            findingRepository.save(
                RegulatoryFinding(
                    recycler    = recycler,
                    check       = check,
                    source      = f.source,
                    findingType = f.findingType,
                    severity    = f.severity,
                    title       = f.title,
                    summary     = f.summary,
                    confidence  = f.confidence
                )
            )
        }

        checkRepository.save(
            check.copy(
                regulatoryStatus  = RegulatoryStatus.COMPLETE,
                regulatoryRisk    = analysis.overallRisk,
                regulatorySummary = analysis.rationale
            )
        )

        log.info("Regulatory research complete for check $checkId — risk: ${analysis.overallRisk}, ${entities.size} finding(s)")
    }

    @Transactional(readOnly = true)
    fun getForCheck(checkId: String, requestingUserId: String): RegulatoryHistoryResponse {
        val check = checkRepository.findByIdFetched(checkId)
            ?: throw NoSuchElementException("Check not found: $checkId")

        if (check.requestedBy.id != requestingUserId) {
            throw SecurityException("Access denied to check: $checkId")
        }

        val findings = findingRepository.findAllByCheckId(checkId)

        return RegulatoryHistoryResponse(
            checkId        = checkId,
            recyclerName   = check.recycler.name,
            bwmrRegNumber  = check.recycler.bwmrRegNumber,
            status         = check.regulatoryStatus,
            overallRisk    = check.regulatoryRisk,
            rationale      = check.regulatorySummary,
            recommendation = null,   // stored in the findings caveat for now
            caveat         = findings.firstOrNull { it.source == "CLAUDE_ANALYSIS" }?.summary,
            findings       = findings.map { it.toDto() }
        )
    }

    private fun RegulatoryFinding.toDto() = RegulatoryFindingDto(
        id          = id!!,
        source      = source,
        findingType = findingType,
        severity    = severity,
        title       = title,
        summary     = summary,
        url         = url,
        findingDate = findingDate,
        confidence  = confidence
    )
}
