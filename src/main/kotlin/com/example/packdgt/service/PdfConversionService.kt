package com.example.packdgt.service

import com.example.packdgt.exception.PdfConversionException
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.office.OfficeManager
import org.jodconverter.local.LocalConverter
import org.jodconverter.local.office.LocalOfficeManager
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Conversion DOCX → PDF via JODConverter + LibreOffice résident.
 *
 * Au lieu de spawner un process soffice par requête (~1.7s de cold start),
 * JODConverter maintient un pool d'instances LibreOffice en mémoire
 * et communique par socket UNO. Résultat : ~100-300ms par conversion.
 *
 * Cycle de vie :
 *   start() → démarre le pool LibreOffice (une fois au démarrage de l'app)
 *   convert() → envoie le DOCX au pool, reçoit le PDF
 *   stop() → arrête proprement les instances LibreOffice
 */
class PdfConversionService(
    private val poolSize: Int = 2,
    private val startPort: Int = 2002,
    private val taskTimeout: Long = 60_000L
) {

    private val logger = LoggerFactory.getLogger(PdfConversionService::class.java)
    private lateinit var officeManager: OfficeManager

    fun start() {
        logger.info("Démarrage du pool LibreOffice (taille={}, ports={}-{})", poolSize, startPort, startPort + poolSize - 1)

        // Note : le message macOS "Task policy set failed: 4 ((os/kern) invalid argument)"
        // est un avertissement inoffensif du noyau — LibreOffice demande une politique de
        // scheduling optionnelle que macOS refuse. Ça n'affecte pas le fonctionnement.
        officeManager = LocalOfficeManager.builder()
            .portNumbers(*IntArray(poolSize) { startPort + it })
            .taskExecutionTimeout(taskTimeout)
            .processTimeout(120_000L)
            .build()

        officeManager.start()
        logger.info("Pool LibreOffice démarré ({} instance(s))", poolSize)
    }

    fun stop() {
        if (::officeManager.isInitialized && officeManager.isRunning) {
            logger.info("Arrêt du pool LibreOffice...")
            officeManager.stop()
            logger.info("Pool LibreOffice arrêté")
        }
    }

    fun convert(docxBytes: ByteArray): ByteArray {
        if (!::officeManager.isInitialized || !officeManager.isRunning) {
            throw PdfConversionException("Le pool LibreOffice n'est pas démarré")
        }

        logger.debug("Conversion DOCX ({} octets) vers PDF via JODConverter", docxBytes.size)

        try {
            val output = ByteArrayOutputStream(docxBytes.size * 2)

            ByteArrayInputStream(docxBytes).use { input ->
                LocalConverter.make(officeManager)
                    .convert(input)
                    .`as`(DefaultDocumentFormatRegistry.DOCX)
                    .to(output)
                    .`as`(DefaultDocumentFormatRegistry.PDF)
                    .execute()
            }

            val pdfBytes = output.toByteArray()
            logger.debug("PDF généré : {} octets", pdfBytes.size)
            return pdfBytes
        } catch (e: Exception) {
            throw PdfConversionException("Échec de la conversion DOCX vers PDF", e)
        }
    }
}
