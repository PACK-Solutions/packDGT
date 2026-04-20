package com.example.packdgt.service

import com.example.packdgt.exception.TemplateNotFoundException
import com.example.packdgt.exception.TemplateProcessingException
import org.apache.poi.xwpf.usermodel.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TemplateService(private val templatesDirectory: String) {

    private val logger = LoggerFactory.getLogger(TemplateService::class.java)
    private val placeholderPattern = Regex("\\{\\{(\\w+)}}")
    private val tableMarkerPattern = Regex("\\{\\{#(\\w+)}}")

    fun process(
        templateName: String,
        data: Map<String, String>,
        tables: Map<String, List<List<String>>> = emptyMap()
    ): ByteArray {
        val templatePath = resolveTemplatePath(templateName)
        logger.debug("Traitement du template : {}", templatePath)

        try {
            Files.newInputStream(templatePath).use { inputStream ->
                XWPFDocument(inputStream).use { document ->
                    val replacedCount = replaceInDocument(document, data)
                    logger.debug("{} remplacement(s) de placeholders effectué(s)", replacedCount)

                    if (tables.isNotEmpty()) {
                        val tablesExpanded = expandTables(document, tables)
                        logger.debug("{} tableau(x) dynamique(s) rempli(s)", tablesExpanded)
                    }

                    ByteArrayOutputStream().use { output ->
                        document.write(output)
                        return output.toByteArray()
                    }
                }
            }
        } catch (e: TemplateNotFoundException) {
            throw e
        } catch (e: Exception) {
            throw TemplateProcessingException("Impossible de traiter '$templateName'", e)
        }
    }

    fun templateExists(templateName: String): Boolean {
        val path = Paths.get(templatesDirectory, templateName)
        return Files.exists(path) && Files.isRegularFile(path)
    }

    fun listTemplates(): List<String> {
        val dir = Paths.get(templatesDirectory)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir)
            .filter { it.toString().endsWith(".docx") }
            .map { it.fileName.toString() }
            .toList()
    }

    private fun resolveTemplatePath(templateName: String): Path {
        val sanitized = Path.of(templateName).fileName.toString()
        val path = Paths.get(templatesDirectory, sanitized)

        if (!Files.exists(path)) {
            throw TemplateNotFoundException(sanitized)
        }
        return path
    }

    // ── Remplacement de placeholders simples ──────────────────────────────

    private fun replaceInDocument(document: XWPFDocument, data: Map<String, String>): Int {
        var totalReplacements = 0

        for (paragraph in document.paragraphs) {
            totalReplacements += replaceInParagraph(paragraph, data)
        }

        for (table in document.tables) {
            for (row in table.rows) {
                for (cell in row.tableCells) {
                    for (paragraph in cell.paragraphs) {
                        totalReplacements += replaceInParagraph(paragraph, data)
                    }
                }
            }
        }

        for (header in document.headerList) {
            for (paragraph in header.paragraphs) {
                totalReplacements += replaceInParagraph(paragraph, data)
            }
        }
        for (footer in document.footerList) {
            for (paragraph in footer.paragraphs) {
                totalReplacements += replaceInParagraph(paragraph, data)
            }
        }

        return totalReplacements
    }

    /**
     * Remplace les placeholders dans un paragraphe.
     *
     * Word peut découper le texte d'un placeholder sur plusieurs runs.
     * On concatène les textes, on effectue les remplacements, puis on
     * réattribue le texte complet au premier run et on vide les autres.
     */
    private fun replaceInParagraph(paragraph: XWPFParagraph, data: Map<String, String>): Int {
        val runs = paragraph.runs ?: return 0
        if (runs.isEmpty()) return 0

        val fullText = runs.joinToString("") { runText(it) ?: "" }
        if (!fullText.contains("{{")) return 0

        var replacedText = fullText
        var count = 0

        for ((key, value) in data) {
            val placeholder = "{{$key}}"
            if (replacedText.contains(placeholder)) {
                replacedText = replacedText.replace(placeholder, value)
                count++
            }
        }

        val remaining = placeholderPattern.findAll(replacedText)
            .map { it.groupValues[1] }
            .filter { !it.startsWith("#") }
            .toList()
        if (remaining.isNotEmpty()) {
            logger.warn("Placeholders non remplacés dans le paragraphe : {}", remaining)
        }

        if (count > 0) {
            runs.first().setText(replacedText, 0)
            for (i in 1 until runs.size) {
                runs[i].setText("", 0)
            }
        }

        return count
    }

    // ── Expansion de tableaux dynamiques ──────────────────────────────────

    /**
     * Cherche les tableaux contenant un marqueur {{#tableName}} dans une cellule,
     * supprime la ligne-modèle et insère les lignes de données à la place.
     *
     * Convention du template :
     *   - Ligne 0 : en-têtes (conservée telle quelle)
     *   - Ligne 1 : contient {{#tableName}} dans la 1re cellule (ligne-modèle, supprimée)
     *   - Les lignes de données sont ajoutées après suppression de la ligne-modèle
     */
    private fun expandTables(document: XWPFDocument, tables: Map<String, List<List<String>>>): Int {
        var expanded = 0

        for (table in document.tables) {
            for (rowIndex in table.rows.indices) {
                val row = table.getRow(rowIndex)
                val firstCellText = row.tableCells.firstOrNull()
                    ?.paragraphs?.firstOrNull()
                    ?.let { p -> p.runs.joinToString("") { runText(it) ?: "" } }
                    ?: continue

                val match = tableMarkerPattern.find(firstCellText) ?: continue
                val tableName = match.groupValues[1]
                val rowsData = tables[tableName] ?: continue

                logger.debug("Expansion du tableau '{}' : {} lignes", tableName, rowsData.size)

                val colCount = row.tableCells.size

                // Supprimer la ligne-modèle
                table.removeRow(rowIndex)

                // Ajouter les lignes de données via l'API POI haut niveau
                for (cells in rowsData) {
                    val newRow = table.createRow()
                    for (colIndex in 0 until colCount) {
                        val value = if (colIndex < cells.size) cells[colIndex] else ""
                        val cell = newRow.getCell(colIndex) ?: newRow.createCell()
                        setCellText(cell, value)
                    }
                }

                expanded++
                break // un seul marqueur par table
            }
        }

        return expanded
    }

    private fun setCellText(cell: XWPFTableCell, text: String) {
        val paragraph = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        val runs = paragraph.runs
        if (runs.isNotEmpty()) {
            runs.first().setText(text, 0)
            for (i in 1 until runs.size) {
                runs[i].setText("", 0)
            }
        } else {
            paragraph.createRun().apply {
                fontSize = 10
                fontFamily = "Arial"
                setText(text)
            }
        }
    }

    private fun runText(run: XWPFRun): String? = run.getText(0)
}
