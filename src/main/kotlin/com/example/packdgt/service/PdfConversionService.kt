package com.example.packdgt.service

import com.example.packdgt.exception.PdfConversionException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Conversion DOCX → PDF via LibreOffice headless.
 *
 * Commande exécutée : soffice --headless --convert-to pdf --outdir <tmp> <docx>
 *
 * Produit un rendu fidèle au DOCX original (polices, tableaux, espacements,
 * images, etc.) contrairement aux convertisseurs purement Java (OpenSagres, docx4j)
 * qui produisent un rendu dégradé.
 *
 * Pré-requis : LibreOffice installé et `soffice` accessible dans le PATH.
 */
class PdfConversionService {

    private val logger = LoggerFactory.getLogger(PdfConversionService::class.java)

    companion object {
        private const val SOFFICE_COMMAND = "soffice"
        private const val TIMEOUT_SECONDS = 30L
    }

    init {
        verifyLibreOfficeAvailable()
    }

    fun convert(docxBytes: ByteArray): ByteArray {
        logger.debug("Conversion DOCX ({} octets) vers PDF via LibreOffice", docxBytes.size)

        val tempDir = Files.createTempDirectory("packdgt-conv-")
        try {
            // Écrire le DOCX dans un fichier temporaire
            val docxFile = tempDir.resolve("document.docx")
            Files.write(docxFile, docxBytes)

            // Exécuter LibreOffice headless
            runLibreOffice(docxFile, tempDir)

            // Lire le PDF résultant
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
            // Nettoyage des fichiers temporaires
            cleanupTempDir(tempDir)
        }
    }

    private fun runLibreOffice(docxFile: Path, outputDir: Path) {
        val command = listOf(
            SOFFICE_COMMAND,
            "--headless",
            "--norestore",
            "--convert-to", "pdf",
            "--outdir", outputDir.toAbsolutePath().toString(),
            docxFile.toAbsolutePath().toString()
        )

        logger.debug("Exécution : {}", command.joinToString(" "))

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

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
            process.waitFor()
            logger.info("LibreOffice détecté : {}", version)
        } catch (e: Exception) {
            logger.error(
                "LibreOffice (soffice) n'est pas disponible dans le PATH. " +
                    "Installer LibreOffice : brew install --cask libreoffice"
            )
            throw PdfConversionException(
                "LibreOffice n'est pas installé ou 'soffice' n'est pas dans le PATH", e
            )
        }
    }

    private fun cleanupTempDir(dir: Path) {
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        } catch (e: Exception) {
            logger.warn("Impossible de nettoyer le répertoire temporaire : {}", dir, e)
        }
    }
}
