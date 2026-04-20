package com.example.packdgt.service

import com.example.packdgt.api.dto.GenerateRequest
import com.example.packdgt.config.AppConfig
import com.example.packdgt.exception.InvalidRequestException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

/**
 * Orchestrateur de la génération documentaire.
 * Pipeline : validation → template POI → conversion PDF → post-traitement PDFBox
 *
 * Chaque étape est chronométrée pour identifier les goulots d'étranglement.
 */
class DocumentGenerationService(
    private val appConfig: AppConfig,
    private val templateService: TemplateService,
    private val pdfConversionService: PdfConversionService,
    private val pdfPostProcessingService: PdfPostProcessingService
) {

    private val logger = LoggerFactory.getLogger(DocumentGenerationService::class.java)

    data class GenerationResult(
        val pdfBytes: ByteArray,
        val fileName: String
    )

    fun generate(request: GenerateRequest): GenerationResult {
        validate(request)

        logger.info("Génération démarrée - template={}, clés={}", request.templateName, request.data.keys)

        val totalStart = System.nanoTime()

        // 1. Remplacement des placeholders et expansion des tableaux (Apache POI)
        lateinit var docxBytes: ByteArray
        val templateMs = measureTimeMillis {
            docxBytes = templateService.process(request.templateName, request.data, request.tables)
        }

        // 2. Conversion DOCX → PDF (LibreOffice headless)
        lateinit var rawPdfBytes: ByteArray
        val conversionMs = measureTimeMillis {
            rawPdfBytes = pdfConversionService.convert(docxBytes)
        }

        // 3. Post-traitement PDF (PDFBox)
        lateinit var finalPdfBytes: ByteArray
        val postProcessMs = measureTimeMillis {
            finalPdfBytes = pdfPostProcessingService.process(rawPdfBytes, request.options)
        }

        val totalMs = (System.nanoTime() - totalStart) / 1_000_000

        // 4. Sauvegarde sur disque si demandé
        val fileName = request.outputFileName ?: "${request.templateName.removeSuffix(".docx")}.pdf"
        val shouldSave = request.options?.saveToDisc ?: appConfig.saveToDisc

        if (shouldSave) {
            saveToDisc(fileName, finalPdfBytes)
        }

        logger.info(
            "Génération terminée - fichier={}, taille={} octets, " +
                "temps=[template={}ms, conversion={}ms, postprocess={}ms, total={}ms]",
            fileName, finalPdfBytes.size,
            templateMs, conversionMs, postProcessMs, totalMs
        )

        return GenerationResult(finalPdfBytes, fileName)
    }

    private fun validate(request: GenerateRequest) {
        if (request.templateName.isBlank()) {
            throw InvalidRequestException("Le nom du template ne peut pas être vide")
        }
        if (!request.templateName.endsWith(".docx")) {
            throw InvalidRequestException("Le template doit être un fichier .docx")
        }
    }

    private fun saveToDisc(fileName: String, pdfBytes: ByteArray) {
        val outputDir = Paths.get(appConfig.outputDirectory)
        Files.createDirectories(outputDir)
        val outputPath = outputDir.resolve(fileName)
        Files.write(outputPath, pdfBytes)
        logger.info("PDF sauvegardé : {}", outputPath.toAbsolutePath())
    }
}
