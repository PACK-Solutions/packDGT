package com.example.packdgt.service

import com.example.packdgt.api.dto.GenerateOptions
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class PdfPostProcessingServiceTest {

    private val service = PdfPostProcessingService()

    private fun createMinimalPdf(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val output = ByteArrayOutputStream()
        doc.save(output)
        doc.close()
        return output.toByteArray()
    }

    @Test
    fun `process retourne le PDF inchangé sans options`() {
        val pdfBytes = createMinimalPdf()
        val result = service.process(pdfBytes, null)
        assertArrayEquals(pdfBytes, result)
    }

    @Test
    fun `process ajoute les métadonnées`() {
        val pdfBytes = createMinimalPdf()
        val options = GenerateOptions(
            author = "Test Author",
            title = "Test Title",
            subject = "Test Subject"
        )

        val result = service.process(pdfBytes, options)
        val doc = Loader.loadPDF(result)

        assertEquals("Test Author", doc.documentInformation.author)
        assertEquals("Test Title", doc.documentInformation.title)
        assertEquals("Test Subject", doc.documentInformation.subject)
        assertEquals("packDGT - Document Generation Tool", doc.documentInformation.creator)

        doc.close()
    }

    @Test
    fun `process applique le watermark sans erreur`() {
        val pdfBytes = createMinimalPdf()
        val options = GenerateOptions(watermark = "CONFIDENTIEL")

        val result = service.process(pdfBytes, options)
        assertNotNull(result)
        assertTrue(result.size > pdfBytes.size, "Le PDF avec watermark doit être plus gros")

        val doc = Loader.loadPDF(result)
        assertEquals(1, doc.numberOfPages)
        doc.close()
    }

    @Test
    fun `process applique la protection`() {
        val pdfBytes = createMinimalPdf()
        val options = GenerateOptions(protect = true)

        val result = service.process(pdfBytes, options)
        val doc = Loader.loadPDF(result)

        assertTrue(doc.isEncrypted, "Le PDF doit être chiffré")
        doc.close()
    }

    @Test
    fun `process ajoute la numérotation des pages`() {
        val doc = PDDocument()
        repeat(3) { doc.addPage(PDPage(PDRectangle.A4)) }
        val output = ByteArrayOutputStream()
        doc.save(output)
        doc.close()

        val options = GenerateOptions(title = "Test Multi-Pages")
        val result = service.process(output.toByteArray(), options)

        val resultDoc = Loader.loadPDF(result)
        assertEquals(3, resultDoc.numberOfPages)
        resultDoc.close()
    }

    @Test
    fun `process cumule watermark, métadonnées et numérotation`() {
        val pdfBytes = createMinimalPdf()
        val options = GenerateOptions(
            watermark = "DRAFT",
            author = "Combo Test",
            title = "Full Options"
        )

        val result = service.process(pdfBytes, options)
        val doc = Loader.loadPDF(result)

        assertEquals("Combo Test", doc.documentInformation.author)
        assertEquals("Full Options", doc.documentInformation.title)
        assertEquals(1, doc.numberOfPages)

        doc.close()
    }
}
