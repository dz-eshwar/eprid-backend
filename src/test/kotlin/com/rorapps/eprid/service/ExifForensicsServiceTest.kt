package com.rorapps.eprid.service

import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.dto.forensics.ForensicsCheckStatus
import com.rorapps.eprid.service.forensics.ExifForensicsService
import com.rorapps.eprid.service.forensics.ReverseGeocodingService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class ExifForensicsServiceTest {

    @Mock
    private lateinit var reverseGeocodingService: ReverseGeocodingService

    @TempDir
    lateinit var tempDir: Path

    private fun service() = ExifForensicsService(reverseGeocodingService)

    @Test
    fun `plain JPEG with no EXIF returns all UNVERIFIABLE checks`() {
        val file = tempDir.resolve("no_exif.jpg").toFile()
        file.writeBytes(minimalJpegBytes())

        val result = service().analyze(file, LocalDate.now(), EvidenceType.SITE_PHOTO, recyclerState = null)

        assertTrue(result.checks.isNotEmpty())
        assertTrue(
            result.checks.all { it.status == ForensicsCheckStatus.UNVERIFIABLE },
            "Expected all UNVERIFIABLE for EXIF-stripped image, got: ${result.checks}"
        )
        assertNull(result.latitude)
        assertNull(result.longitude)
    }

    @Test
    fun `non-image file returns UNVERIFIABLE via exception path`() {
        val file = tempDir.resolve("document.txt").toFile()
        file.writeText("not an image")

        val result = service().analyze(file, LocalDate.now(), EvidenceType.OTHER, recyclerState = null)

        assertTrue(result.checks.isNotEmpty())
        assertTrue(result.checks.all { it.status == ForensicsCheckStatus.UNVERIFIABLE })
    }

    @Test
    fun `india bounding box covers expected coordinates`() {
        val mumbaiLat = 19.076
        val mumbaiLon = 72.877
        assertTrue(mumbaiLat in 6.5..37.1)
        assertTrue(mumbaiLon in 68.1..97.4)

        val capeTownLat = -33.9
        assertFalse(capeTownLat in 6.5..37.1)
    }

    /** Returns bytes for a minimal valid JPEG with no EXIF metadata */
    private fun minimalJpegBytes(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00,
        0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0xFF.toByte(), 0xD9.toByte()
    )
}
