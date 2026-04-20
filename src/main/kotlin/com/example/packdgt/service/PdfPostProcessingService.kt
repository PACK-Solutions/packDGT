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
 * Post-traitement PDF avec Apache PDFBox 3 :
 * - Ajout de métadonnées (auteur, titre, sujet)
 * - Watermark en filigrane
 * - Protection simple en lecture
 * - Numérotation des pages (footer)
 */
class PdfPostProcessingService {

    private val logger = LoggerFactory.getLogger(PdfPostProcessingService::class.java)

    fun process(pdfBytes: ByteArray, options: GenerateOptions?): ByteArray {
        if (options == null) return pdfBytes

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

                ByteArrayOutputStream().use { output ->
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

    private fun applyMetadata(document: PDDocument, options: GenerateOptions) {
        val info = document.documentInformation
        options.author?.let { info.author = it }
        options.title?.let { info.title = it }
        options.subject?.let { info.subject = it }
        info.creator = "packDGT - Document Generation Tool"
        info.producer = "Apache PDFBox"
        info.creationDate = Calendar.getInstance()
        info.modificationDate = Calendar.getInstance()
        logger.debug("Métadonnées PDF appliquées")
    }

    private fun applyWatermark(document: PDDocument, text: String) {
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontSize = 52f
        val angle = Math.toRadians(45.0)

        for (page in document.pages) {
            val pageSize = page.mediaBox ?: PDRectangle.A4
            val centerX = pageSize.width / 2
            val centerY = pageSize.height / 2

            PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true, true
            ).use { cs ->
                // Transparence
                val gs = PDExtendedGraphicsState()
                gs.nonStrokingAlphaConstant = 0.15f
                cs.setGraphicsStateParameters(gs)

                cs.setFont(font, fontSize)
                cs.setNonStrokingColor(Color(150, 150, 150))
                cs.beginText()

                // Matrice de rotation centrée
                val cos = Math.cos(angle).toFloat()
                val sin = Math.sin(angle).toFloat()

                // Approximation du centrage du texte
                val textWidth = font.getStringWidth(text) / 1000 * fontSize
                val offsetX = centerX - (textWidth / 2 * cos)
                val offsetY = centerY - (textWidth / 2 * sin)

                cs.setTextMatrix(Matrix(cos, sin, -sin, cos, offsetX, offsetY))
                cs.showText(text)
                cs.endText()
            }
        }
        logger.debug("Watermark '{}' appliqué sur {} page(s)", text, document.numberOfPages)
    }

    private fun addPageNumbers(document: PDDocument) {
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontSize = 9f
        val totalPages = document.numberOfPages

        for ((index, page) in document.pages.withIndex()) {
            val pageSize = page.mediaBox ?: PDRectangle.A4
            val text = "Page ${index + 1} / $totalPages"

            PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND,
                true, true
            ).use { cs ->
                cs.setFont(font, fontSize)
                cs.setNonStrokingColor(Color(100, 100, 100))
                cs.beginText()

                val textWidth = font.getStringWidth(text) / 1000 * fontSize
                val x = (pageSize.width - textWidth) / 2
                val y = 25f

                cs.newLineAtOffset(x, y)
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
