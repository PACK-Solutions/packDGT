package com.example.packdgt.service

import com.example.packdgt.exception.TemplateNotFoundException
import com.example.packdgt.exception.TemplateProcessingException
import org.apache.poi.xwpf.usermodel.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class TemplateService(private val templatesDirectory: String) {

    private val logger = LoggerFactory.getLogger(TemplateService::class.java)
    private val placeholderPattern = Regex("\\{\\{(\\w+)}}")
    private val tableMarkerPattern = Regex("\\{\\{#(\\w+)}}")

    /**
     * Cache en mémoire des templates DOCX.
     * Clé = nom du fichier, Valeur = (bytes, lastModified).
     * Invalidation automatique si le fichier est modifié sur disque.
     */
    private data class CachedTemplate(val bytes: ByteArray, val lastModified: Long)
    private val templateCache = ConcurrentHashMap<String, CachedTemplate>()

    fun process(
        templateName: String,
        data: Map<String, String>,
        tables: Map<String, List<List<String>>> = emptyMap()
    ): ByteArray {
        val templatePath = resolveTemplatePath(templateName)
        val templateBytes = loadCachedTemplate(templateName, templatePath)

        logger.debug("Traitement du template : {} ({} octets, cache)", templatePath, templateBytes.size)

        try {
            ByteArrayInputStream(templateBytes).use { inputStream ->
                XWPFDocument(inputStream).use { document ->
                    val replacedCount = replaceInDocument(document, data)
                    logger.debug("{} remplacement(s) de placeholders effectué(s)", replacedCount)

                    if (tables.isNotEmpty()) {
                        val tablesExpanded = expandTables(document, tables)
                        logger.debug("{} tableau(x) dynamique(s) rempli(s)", tablesExpanded)
                    }

                    ByteArrayOutputStream(templateBytes.size).use { output ->
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

    /**
     * Charge le template depuis le cache mémoire.
     * Invalidation si le fichier a été modifié sur disque (comparaison lastModified).
     */
    private fun loadCachedTemplate(name: String, path: Path): ByteArray {
        val currentModified = Files.getLastModifiedTime(path).toMillis()
        val cached = templateCache[name]

        if (cached != null && cached.lastModified == currentModified) {
            return cached.bytes
        }

        val bytes = Files.readAllBytes(path)
        templateCache[name] = CachedTemplate(bytes, currentModified)
        logger.debug("Template '{}' chargé en cache ({} octets)", name, bytes.size)
        return bytes
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

                table.removeRow(rowIndex)

                for (cells in rowsData) {
                    val newRow = table.createRow()
                    for (colIndex in 0 until colCount) {
                        val value = if (colIndex < cells.size) cells[colIndex] else ""
                        val cell = newRow.getCell(colIndex) ?: newRow.createCell()
                        setCellText(cell, value)
                    }
                }

                expanded++
                break
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
