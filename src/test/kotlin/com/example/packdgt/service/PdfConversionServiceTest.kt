package com.example.packdgt.service

import com.example.packdgt.tools.createSampleTemplate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfConversionServiceTest {

    private val testTemplatesDir = "build/test-templates-conv"
    private val conversionService = PdfConversionService()
    private val templateService = TemplateService(testTemplatesDir)

    @BeforeAll
    fun setup() {
        createSampleTemplate(testTemplatesDir)
    }

    @Test
    fun `convert produit un PDF valide a partir d'un DOCX`() {
        val data = mapOf(
            "customerName" to "Test User",
            "policyNumber" to "POL-TEST",
            "startDate" to "2026-01-01",
            "endDate" to "2026-12-31",
            "premium" to "500 €",
            "address" to "1 rue Test"
        )

        val docxBytes = templateService.process("attestation-assurance.docx", data)
        val pdfBytes = conversionService.convert(docxBytes)

        assertNotNull(pdfBytes)
        assertTrue(pdfBytes.size > 500, "Le PDF doit avoir une taille significative")
        assertTrue(pdfBytes[0] == '%'.code.toByte(), "Doit commencer par %PDF")

        val doc = Loader.loadPDF(pdfBytes)
        assertTrue(doc.numberOfPages >= 1, "Le PDF doit avoir au moins une page")

        // Vérifier que le contenu textuel est présent dans le PDF
        val text = PDFTextStripper().getText(doc)
        assertTrue(text.contains("Test User"), "Le PDF doit contenir le nom remplacé")
        assertTrue(text.contains("POL-TEST"), "Le PDF doit contenir le numéro de police")
        assertTrue(text.contains("ASSURANCE EXEMPLE"), "Le PDF doit contenir l'en-tête")

        doc.close()
    }

    @Test
    fun `convert produit un PDF avec tableaux dynamiques`() {
        val data = mapOf("customerName" to "Dupont")
        val tables = mapOf(
            "movements" to listOf(
                listOf("01/04/2026", "Prélèvement", "-100 €", "900 €"),
                listOf("01/03/2026", "Versement", "+1000 €", "1000 €")
            )
        )

        val docxBytes = templateService.process("attestation-assurance.docx", data, tables)
        val pdfBytes = conversionService.convert(docxBytes)

        val doc = Loader.loadPDF(pdfBytes)
        val text = PDFTextStripper().getText(doc)

        assertTrue(text.contains("Prélèvement"), "Le PDF doit contenir les données du tableau")
        assertTrue(text.contains("Versement"), "Le PDF doit contenir les données du tableau")

        doc.close()
    }

    @AfterAll
    fun cleanup() {
        val dir = Paths.get(testTemplatesDir)
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
