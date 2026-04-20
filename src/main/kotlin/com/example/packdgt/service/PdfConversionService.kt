package com.example.packdgt.service

import com.example.packdgt.exception.PdfConversionException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Conversion DOCX → PDF via LibreOffice headless.
 *
 * Optimisations :
 * - Chaque requête concurrente utilise son propre UserInstallation (pas de lock profil)
 * - Timeout configurable sur le process
 * - Nettoyage asynchrone des fichiers temporaires
 */
class PdfConversionService(
    private val timeoutSeconds: Long = 30L,
    private val maxConcurrent: Int = 4
) {

    private val logger = LoggerFactory.getLogger(PdfConversionService::class.java)
    private val instanceCounter = AtomicInteger(0)

    companion object {
        private const val SOFFICE_COMMAND = "soffice"
    }

    init {
        verifyLibreOfficeAvailable()
    }

    fun convert(docxBytes: ByteArray): ByteArray {
        logger.debug("Conversion DOCX ({} octets) vers PDF via LibreOffice", docxBytes.size)

        val instanceId = instanceCounter.getAndIncrement() % maxConcurrent
        val tempDir = Files.createTempDirectory("packdgt-conv-")
        val userInstallDir = tempDir.resolve("user-$instanceId")
        Files.createDirectories(userInstallDir)

        try {
            val docxFile = tempDir.resolve("document.docx")
            Files.write(docxFile, docxBytes)

            runLibreOffice(docxFile, tempDir, userInstallDir)

            val pdfFile = tempDir.resolve("document.pdf")
            if (!Files.exists(pdfFile)) {
                throw PdfConversionException("LibreOffice n'a pas produit de fichier PDF")
            }

            val pdfBytes = Files.readAllBytes(pdfFile)
            logger.debug("PDF généré : {} octets", pdfBytes.size)
            return pdfBytes
        } catch (e: PdfConversionException) {
            throw e
        } catch (e: Exception) {
            throw PdfConversionException("Échec de la conversion DOCX vers PDF", e)
        } finally {
            cleanupAsync(tempDir)
        }
    }

    private fun runLibreOffice(docxFile: Path, outputDir: Path, userInstallDir: Path) {
        val command = listOf(
            SOFFICE_COMMAND,
            "--headless",
            "--norestore",
            "--nofirststartwizard",
            "-env:UserInstallation=file://${userInstallDir.toAbsolutePath()}",
            "--convert-to", "pdf",
            "--outdir", outputDir.toAbsolutePath().toString(),
            docxFile.toAbsolutePath().toString()
        )

        logger.debug("Exécution : {}", command.joinToString(" "))

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            throw PdfConversionException(
                "LibreOffice a dépassé le timeout de ${timeoutSeconds}s"
            )
        }

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.exitValue()

        if (exitCode != 0) {
            logger.error("LibreOffice a échoué (code {}): {}", exitCode, output)
            throw PdfConversionException(
                "LibreOffice a retourné le code $exitCode. Sortie : ${output.take(500)}"
            )
        }

        logger.debug("LibreOffice terminé : {}", output.trim())
    }

    private fun verifyLibreOfficeAvailable() {
        try {
            val process = ProcessBuilder(SOFFICE_COMMAND, "--version")
                .redirectErrorStream(true)
                .start()
            val version = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(5, TimeUnit.SECONDS)
            logger.info("LibreOffice détecté : {}", version)
        } catch (e: Exception) {
            throw PdfConversionException(
                "LibreOffice n'est pas installé ou 'soffice' n'est pas dans le PATH", e
            )
        }
    }

    /**
     * Nettoyage asynchrone des fichiers temporaires pour ne pas bloquer la réponse.
     */
    private fun cleanupAsync(dir: Path) {
        Thread.startVirtualThread {
            try {
                Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            } catch (e: Exception) {
                logger.warn("Impossible de nettoyer : {}", dir, e)
            }
        }
    }
}
