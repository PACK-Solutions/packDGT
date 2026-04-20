package com.example.packdgt.service

import com.example.packdgt.api.dto.GenerateOptions
import com.example.packdgt.api.dto.GenerateRequest
import com.example.packdgt.config.AppConfig
import com.example.packdgt.exception.InvalidRequestException
import com.example.packdgt.exception.TemplateNotFoundException
import com.example.packdgt.tools.createSampleTemplate
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentGenerationServiceTest {

    private val testTemplatesDir = "build/test-templates-gen"
    private val testOutputDir = "build/test-output"
    private lateinit var service: DocumentGenerationService

    @BeforeAll
    fun setup() {
        createSampleTemplate(testTemplatesDir)
        val config = AppConfig(
            templatesDirectory = testTemplatesDir,
            outputDirectory = testOutputDir,
            saveToDisc = false
        )
        service = DocumentGenerationService(
            appConfig = config,
            templateService = TemplateService(testTemplatesDir),
            pdfConversionService = PdfConversionService(),
            pdfPostProcessingService = PdfPostProcessingService()
        )
    }

    @Test
    fun `generate produit un PDF complet avec toutes les options`() {
        val request = GenerateRequest(
            templateName = "attestation-assurance.docx",
            outputFileName = "test-complet.pdf",
            data = mapOf(
                "customerName" to "Alice Durand",
                "policyNumber" to "POL-2026-456",
                "startDate" to "2026-06-01",
                "endDate" to "2027-05-31",
                "premium" to "2100 €",
                "address" to "15 boulevard Test, 69001 Lyon"
            ),
            tables = mapOf(
                "movements" to listOf(
                    listOf("01/06/2026", "Souscription", "-2100 €", "7 900,00 €"),
                    listOf("15/05/2026", "Virement initial", "+10 000 €", "10 000,00 €")
                )
            ),
            options = GenerateOptions(
                watermark = "SPECIMEN",
                author = "Test Gen",
                title = "Attestation Test"
            )
        )

        val result = service.generate(request)

        assertEquals("test-complet.pdf", result.fileName)
        assertTrue(result.pdfBytes.isNotEmpty())

        val doc = Loader.loadPDF(result.pdfBytes)
        assertTrue(doc.numberOfPages >= 1)
        assertEquals("Test Gen", doc.documentInformation.author)
        assertEquals("Attestation Test", doc.documentInformation.title)
        doc.close()
    }

    @Test
    fun `generate utilise le nom du template comme nom de fichier par défaut`() {
        val request = GenerateRequest(
            templateName = "attestation-assurance.docx",
            data = mapOf("customerName" to "Défaut")
        )

        val result = service.generate(request)
        assertEquals("attestation-assurance.pdf", result.fileName)
    }

    @Test
    fun `generate sauvegarde sur disque quand demandé`() {
        val request = GenerateRequest(
            templateName = "attestation-assurance.docx",
            outputFileName = "saved-on-disc.pdf",
            data = mapOf("customerName" to "Disc Test"),
            options = GenerateOptions(saveToDisc = true)
        )

        service.generate(request)

        val outputPath = Paths.get(testOutputDir, "saved-on-disc.pdf")
        assertTrue(Files.exists(outputPath), "Le PDF doit être sauvegardé sur disque")
        assertTrue(Files.size(outputPath) > 0)
    }

    @Test
    fun `generate echoue avec un templateName vide`() {
        val request = GenerateRequest(templateName = "", data = emptyMap())
        assertThrows<InvalidRequestException> { service.generate(request) }
    }

    @Test
    fun `generate echoue avec une extension non-docx`() {
        val request = GenerateRequest(templateName = "template.pdf", data = emptyMap())
        assertThrows<InvalidRequestException> { service.generate(request) }
    }

    @Test
    fun `generate echoue avec un template inexistant`() {
        val request = GenerateRequest(templateName = "fantome.docx", data = emptyMap())
        assertThrows<TemplateNotFoundException> { service.generate(request) }
    }

    @AfterAll
    fun cleanup() {
        listOf(testTemplatesDir, testOutputDir).forEach { dirPath ->
            val dir = Paths.get(dirPath)
            if (Files.exists(dir)) {
                Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
