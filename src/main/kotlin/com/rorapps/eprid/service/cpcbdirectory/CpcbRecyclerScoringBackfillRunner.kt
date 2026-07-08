package com.rorapps.eprid.service.cpcbdirectory

import com.rorapps.eprid.repository.CpcbRecyclerRepository
import com.rorapps.eprid.repository.CpcbRecyclerScoreRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Recycler master data ships via Flyway migration (V13), not runtime logic — see that file's
 * header comment. Composite scores are different: they're derived/recomputable data (the scoring
 * formula itself can change), so they don't belong in a migration. This runs on every startup and
 * scores any recycler that doesn't have one yet — a fresh DB after V13 runs, or any recycler added
 * later via /cpcb-recyclers/ingest that hasn't been scored for some reason.
 */
@Component
class CpcbRecyclerScoringBackfillRunner(
    private val recyclerRepository: CpcbRecyclerRepository,
    private val scoreRepository: CpcbRecyclerScoreRepository,
    private val scoringService: CpcbRecyclerScoringService
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val unscored = recyclerRepository.findAll()
            .filter { scoreRepository.findFirstByRecyclerIdOrderByScoredAtDesc(it.id!!) == null }

        if (unscored.isEmpty()) {
            log.info("CPCB recycler directory: all recyclers already scored")
            return
        }

        log.info("CPCB recycler directory: scoring {} recycler(s) with no score on file", unscored.size)
        unscored.forEach { scoringService.scoreAndSave(it) }
    }
}
