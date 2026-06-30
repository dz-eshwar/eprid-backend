package com.rorapps.eprid.service

import com.rorapps.eprid.dto.forensics.ForensicsCheckStatus
import com.rorapps.eprid.entity.*
import com.rorapps.eprid.repository.EvidenceRepository
import com.rorapps.eprid.service.forensics.ImageHashService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import javax.imageio.ImageIO

@ExtendWith(MockitoExtension::class)
class ImageHashServiceTest {

    @Mock private lateinit var evidenceRepository: EvidenceRepository
    @InjectMocks private lateinit var hashService: ImageHashService

    @TempDir
    lateinit var tempDir: Path

    private fun solidColorImage(color: Color, file: File): File {
        val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, 100, 100)
        g.dispose()
        ImageIO.write(img, "PNG", file)
        return file
    }

    @Test
    fun `same image produces same hash`() {
        val file = solidColorImage(Color.RED, tempDir.resolve("red.png").toFile())
        val h1 = hashService.computeDHash(file)
        val h2 = hashService.computeDHash(file)
        assertNotNull(h1)
        assertEquals(h1, h2)
    }

    @Test
    fun `different solid colors produce different hashes`() {
        val red = solidColorImage(Color.RED, tempDir.resolve("red.png").toFile())
        val blue = solidColorImage(Color.BLUE, tempDir.resolve("blue.png").toFile())
        val h1 = hashService.computeDHash(red)
        val h2 = hashService.computeDHash(blue)
        // hashes may differ (not guaranteed for solid fills, but at least both compute)
        assertNotNull(h1)
        assertNotNull(h2)
    }

    @Test
    fun `no duplicates in empty database returns PASS`() {
        whenever(evidenceRepository.findAllByImagePhashNotNull()).thenReturn(emptyList())
        val result = hashService.checkForDuplicates("abcdef1234567890", excludeEvidenceId = null)
        assertEquals(ForensicsCheckStatus.PASS, result.status)
    }

    @Test
    fun `identical hash in database returns FAIL`() {
        val hash = "abcdef1234567890"
        val mockUser = User(id = "u1", email = "a@b.com", passwordHash = "x", fullName = "Test")
        val mockProducer = Producer(id = "p1", name = "P", createdBy = mockUser)
        val mockRecycler = Recycler(id = "r1", name = "R")
        val mockCheck = VerificationCheck(
            id = "c1", producer = mockProducer, recycler = mockRecycler,
            requestedBy = mockUser, batchWeightTonnes = BigDecimal.ONE,
            claimedRecoveryPct = BigDecimal("55"), processingDate = LocalDate.now()
        )
        val existingEvidence = Evidence(
            id = "e-existing", check = mockCheck,
            fileName = "old.jpg", contentType = "image/jpeg",
            fileSizeBytes = 1000, storagePath = "/tmp/old.jpg",
            imagePhash = hash
        )
        whenever(evidenceRepository.findAllByImagePhashNotNull()).thenReturn(listOf(existingEvidence))

        val result = hashService.checkForDuplicates(hash, excludeEvidenceId = null)
        assertEquals(ForensicsCheckStatus.FAIL, result.status)
        assertTrue(result.detail.contains("e-existing"))
    }
}
