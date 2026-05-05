package com.example.packdgt.service

import com.example.packdgt.api.dto.GenerateOptions
import com.example.packdgt.api.dto.GenerateRequest
import com.example.packdgt.config.AppConfig
import com.example.packdgt.exception.InvalidRequestException
import com.example.packdgt.exception.PdfPostProcessingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
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

    fun generate(
        request: GenerateRequest,
        freeText: String? = null,
        freeTextPlaceholder: Boolean = false
    ): GenerationResult {
        validate(request)

        logger.info("Génération démarrée - template={}, clés={}", request.templateName, request.data.keys)

        val totalStart = System.nanoTime()

        // 1. Remplacement des placeholders et expansion des tableaux (Apache POI)
        lateinit var docxBytes: ByteArray
        val templateMs = measureTimeMillis {
            docxBytes = templateService.process(request.templateName, request.data, request.tables, freeText, freeTextPlaceholder)
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

    /**
     * Génération de masse : produit un PDF unique contenant `count` exemplaires du template.
     *
     * Pipeline :
     *   1. Pour chaque i ∈ [1..count] : POI + DOCX→PDF (sans post-traitement) en parallèle,
     *      avec parallélisme borné par la taille du pool LibreOffice.
     *      Pour rendre les documents distincts, certains champs identifiants (numeroAdherent,
     *      numeroContrat, identifiantClient, customerName, policyNumber) reçoivent un suffixe -NNN.
     *   2. Fusion PDFBox (PDFMergerUtility).
     *   3. Post-traitement appliqué une seule fois sur le PDF fusionné (watermark, méta, pagination).
     */
    suspend fun generateBatch(
        templateName: String,
        count: Int,
        data: Map<String, String>,
        tables: Map<String, List<List<String>>>,
        options: GenerateOptions?,
        outputFileName: String?
    ): GenerationResult {
        if (templateName.isBlank() || !templateName.endsWith(".docx")) {
            throw InvalidRequestException("Le template doit être un fichier .docx non vide")
        }
        if (count < 1 || count > MAX_BATCH_COUNT) {
            throw InvalidRequestException("Le nombre de documents doit être entre 1 et $MAX_BATCH_COUNT (reçu : $count)")
        }

        logger.info("Génération en lot démarrée - template={}, count={}", templateName, count)
        val totalStart = System.nanoTime()

        val parallelism = appConfig.libreOfficePoolSize.coerceAtLeast(1)
        val semaphore = Semaphore(parallelism)

        // 1. Génération parallèle des PDF bruts (sans post-traitement)
        val rawPdfs: List<ByteArray>
        val genMs = measureTimeMillis {
            rawPdfs = coroutineScope {
                (1..count).map { i ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val variedData = varyData(data, i, count)
                            val docxBytes = templateService.process(templateName, variedData, tables)
                            pdfConversionService.convert(docxBytes)
                        }
                    }
                }.awaitAll()
            }
        }

        // 2. Fusion
        lateinit var mergedPdf: ByteArray
        val mergeMs = measureTimeMillis {
            mergedPdf = withContext(Dispatchers.IO) { mergePdfs(rawPdfs) }
        }

        // 3. Post-traitement sur le résultat fusionné
        lateinit var finalPdf: ByteArray
        val postProcessMs = measureTimeMillis {
            finalPdf = pdfPostProcessingService.process(mergedPdf, options)
        }

        val totalMs = (System.nanoTime() - totalStart) / 1_000_000

        val fileName = outputFileName
            ?: "${templateName.removeSuffix(".docx")}-batch-$count.pdf"

        val shouldSave = options?.saveToDisc ?: appConfig.saveToDisc
        if (shouldSave) {
            saveToDisc(fileName, finalPdf)
        }

        logger.info(
            "Génération en lot terminée - fichier={}, count={}, taille={} octets, " +
                "temps=[génération={}ms ({} ms/doc), fusion={}ms, postprocess={}ms, total={}ms], parallélisme={}",
            fileName, count, finalPdf.size,
            genMs, genMs / count, mergeMs, postProcessMs, totalMs,
            parallelism
        )

        return GenerationResult(finalPdf, fileName)
    }

    private fun mergePdfs(pdfs: List<ByteArray>): ByteArray {
        try {
            val output = ByteArrayOutputStream(pdfs.sumOf { it.size } + 4096)
            val merger = PDFMergerUtility()
            merger.destinationStream = output
            for (pdf in pdfs) {
                merger.addSource(RandomAccessReadBuffer(pdf))
            }
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache())
            return output.toByteArray()
        } catch (e: Exception) {
            throw PdfPostProcessingException("Échec de la fusion des PDF", e)
        }
    }

    /**
     * Personnalise les données pour le i-ème document afin que les exemplaires fusionnés
     * soient distinguables visuellement. Suffixe de la forme "-NNN" appliqué sur les champs
     * identifiants connus, sans modifier les autres valeurs (montants, dates, etc.).
     */
    private fun varyData(base: Map<String, String>, index: Int, total: Int): Map<String, String> {
        if (base.isEmpty()) return base
        val width = total.toString().length
        val padded = index.toString().padStart(width, '0')
        val varied = HashMap(base)
        for (key in IDENTITY_KEYS) {
            val current = varied[key] ?: continue
            varied[key] = "$current-$padded"
        }
        return varied
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

    companion object {
        const val MAX_BATCH_COUNT = 1000
        private val IDENTITY_KEYS = listOf(
            "numeroAdherent",
            "numeroContrat",
            "identifiantClient",
            "policyNumber",
            "customerName"
        )
    }
}
