package com.rorapps.eprid.service.einvoice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

class QrCodeExtractorTest {

    private val extractor = QrCodeExtractor()

    @Test
    fun `image with no QR code returns null`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("plain.png").toFile()
        val img = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB).also {
            val g = it.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, 200, 200)
            g.dispose()
        }
        ImageIO.write(img, "PNG", file)

        assertNull(extractor.extractFromImage(file))
    }
}
