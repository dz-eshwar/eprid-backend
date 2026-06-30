package com.rorapps.eprid.service.forensics

import com.rorapps.eprid.dto.forensics.ForensicsCheckResult
import com.rorapps.eprid.dto.forensics.ForensicsCheckStatus
import com.rorapps.eprid.repository.EvidenceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@Service
class ImageHashService(private val evidenceRepository: EvidenceRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Difference Hash (dHash): resize to 9×8 greyscale, compare adjacent pixels.
     * Returns a 64-bit hash as a 16-char hex string.
     */
    fun computeDHash(file: File): String? = runCatching {
        val img = ImageIO.read(file) ?: return null
        val resized = BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY).also { out ->
            out.createGraphics().apply {
                drawImage(img.getScaledInstance(9, 8, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
                dispose()
            }
        }
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = resized.getRGB(x, y) and 0xFF
                val right = resized.getRGB(x + 1, y) and 0xFF
                hash = (hash shl 1) or (if (left > right) 1L else 0L)
            }
        }
        "%016x".format(hash)
    }.getOrNull()

    /** Hamming distance between two 64-bit hex hashes — ≤10 bits = near-duplicate */
    private fun hammingDistance(a: String, b: String): Int {
        // parseUnsignedLong handles full 64-bit unsigned values that exceed Long.MAX_VALUE
        val aLong = java.lang.Long.parseUnsignedLong(a, 16)
        val bLong = java.lang.Long.parseUnsignedLong(b, 16)
        return java.lang.Long.bitCount(aLong xor bLong)
    }

    fun checkForDuplicates(newHash: String, excludeEvidenceId: String?): ForensicsCheckResult {
        val existing = evidenceRepository.findAllByImagePhashNotNull()
            .filter { it.id != excludeEvidenceId && it.imagePhash != null }

        val duplicate = existing.firstOrNull { hammingDistance(it.imagePhash!!, newHash) <= 10 }

        return if (duplicate != null) {
            ForensicsCheckResult(
                checkName = "Reverse image duplicate check",
                status = ForensicsCheckStatus.FAIL,
                detail = "This image is visually near-identical to evidence already on file " +
                         "(evidence ID: ${duplicate.id}, check ID: ${duplicate.check.id}) — " +
                         "possible reuse of a photo across multiple certificates"
            )
        } else {
            ForensicsCheckResult(
                checkName = "Reverse image duplicate check",
                status = ForensicsCheckStatus.PASS,
                detail = "No visually identical image found in the evidence database"
            )
        }
    }
}
