package com.example.packdgt.service

import com.example.packdgt.api.dto.GenerateOptions
import com.example.packdgt.exception.PdfPostProcessingException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Post-traitement PDF avec Apache PDFBox 3.
 *
 * Optimisations :
 * - Instances de fonts pré-créées et réutilisées (thread-safe en lecture seule)
 * - Objets graphiques constants partagés
 * - Short-circuit si aucune option n'a d'effet
 */
class PdfPostProcessingService {

    private val logger = LoggerFactory.getLogger(PdfPostProcessingService::class.java)

    // Fonts réutilisables (PDType1Font est thread-safe en lecture)
    private val watermarkFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val pageNumberFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)

    // Constantes graphiques
    private val watermarkColor = Color(150, 150, 150)
    private val pageNumberColor = Color(100, 100, 100)
    private val watermarkAlpha = 0.15f
    private val watermarkAngle = Math.toRadians(45.0)
    private val watermarkFontSize = 52f
    private val pageNumberFontSize = 9f
    private val watermarkCos = Math.cos(watermarkAngle).toFloat()
    private val watermarkSin = Math.sin(watermarkAngle).toFloat()

    fun process(pdfBytes: ByteArray, options: GenerateOptions?): ByteArray {
        if (options == null) return pdfBytes

        // Short-circuit : si aucune option n'a d'effet réel
        if (!hasWork(options)) {
            return applyMetadataOnly(pdfBytes, options)
        }

        try {
            Loader.loadPDF(pdfBytes).use { document ->
                applyMetadata(document, options)

                if (options.watermark != null) {
                    applyWatermark(document, options.watermark)
                }

                addPageNumbers(document)

                if (options.protect) {
                    applyProtection(document)
                }

                ByteArrayOutputStream(pdfBytes.size + 4096).use { output ->
                    document.save(output)
                    return output.toByteArray()
                }
            }
        } catch (e: PdfPostProcessingException) {
            throw e
        } catch (e: Exception) {
            throw PdfPostProcessingException("Échec du post-traitement PDF", e)
        }
    }

    private fun hasWork(options: GenerateOptions): Boolean {
        return options.watermark != null || options.protect
    }

    /**
     * Chemin rapide : uniquement métadonnées + pagination, pas de watermark/protection.
     */
    private fun applyMetadataOnly(pdfBytes: ByteArray, options: GenerateOptions): ByteArray {
        try {
            Loader.loadPDF(pdfBytes).use { document ->
                applyMetadata(document, options)
                addPageNumbers(document)

                ByteArrayOutputStream(pdfBytes.size + 1024).use { output ->
                    document.save(output)
                    return output.toByteArray()
                }
            }
        } catch (e: Exception) {
            throw PdfPostProcessingException("Échec du post-traitement PDF", e)
        }
    }

    private fun applyMetadata(document: PDDocument, options: GenerateOptions) {
        val info = document.documentInformation
        options.author?.let { info.author = it }
        options.title?.let { info.title = it }
        options.subject?.let { info.subject = it }
        info.creator = "packDGT - Document Generation Tool"
        info.producer = "Apache PDFBox"
        val now = Calendar.getInstance()
        info.creationDate = now
        info.modificationDate = now
        logger.debug("Métadonnées PDF appliquées")
    }

    private fun applyWatermark(document: PDDocument, text: String) {
        // Pré-calculer la largeur du texte (invariant par page)
        val textWidth = watermarkFont.getStringWidth(text) / 1000 * watermarkFontSize

        for (page in document.pages) {
            val pageSize = page.mediaBox ?: PDRectangle.A4
            val centerX = pageSize.width / 2
            val centerY = pageSize.height / 2

            PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true, true
            ).use { cs ->
                val gs = PDExtendedGraphicsState()
                gs.nonStrokingAlphaConstant = watermarkAlpha
                cs.setGraphicsStateParameters(gs)

                cs.setFont(watermarkFont, watermarkFontSize)
                cs.setNonStrokingColor(watermarkColor)
                cs.beginText()

                val offsetX = centerX - (textWidth / 2 * watermarkCos)
                val offsetY = centerY - (textWidth / 2 * watermarkSin)

                cs.setTextMatrix(Matrix(watermarkCos, watermarkSin, -watermarkSin, watermarkCos, offsetX, offsetY))
                cs.showText(text)
                cs.endText()
            }
        }
        logger.debug("Watermark '{}' appliqué sur {} page(s)", text, document.numberOfPages)
    }

    private fun addPageNumbers(document: PDDocument) {
        val totalPages = document.numberOfPages

        for ((index, page) in document.pages.withIndex()) {
            val pageSize = page.mediaBox ?: PDRectangle.A4
            val text = "Page ${index + 1} / $totalPages"

            PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true, true
            ).use { cs ->
                cs.setFont(pageNumberFont, pageNumberFontSize)
                cs.setNonStrokingColor(pageNumberColor)
                cs.beginText()

                val textWidth = pageNumberFont.getStringWidth(text) / 1000 * pageNumberFontSize
                val x = (pageSize.width - textWidth) / 2

                cs.newLineAtOffset(x, 25f)
                cs.showText(text)
                cs.endText()
            }
        }
        logger.debug("Numérotation ajoutée sur {} page(s)", totalPages)
    }

    private fun applyProtection(document: PDDocument) {
        val permissions = AccessPermission()
        permissions.setCanPrint(true)
        permissions.setCanExtractContent(false)
        permissions.setCanModify(false)
        permissions.setCanModifyAnnotations(false)

        val policy = StandardProtectionPolicy("", "", permissions)
        policy.encryptionKeyLength = 128

        document.protect(policy)
        logger.debug("Protection PDF appliquée (impression autorisée, modification interdite)")
    }
}
