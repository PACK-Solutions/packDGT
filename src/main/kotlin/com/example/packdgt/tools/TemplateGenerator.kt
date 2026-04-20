package com.example.packdgt.tools

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    createSampleTemplate("templates")
    println("Template d'exemple créé dans templates/attestation-assurance.docx")
}

fun createSampleTemplate(outputDir: String) {
    val doc = XWPFDocument()

    doc.createStyles()
    setupPageFormat(doc)

    // ── En-tête entreprise ──
    paragraph(doc, ParagraphAlignment.CENTER) {
        run(bold = true, size = 16) { setText("ASSURANCE EXEMPLE SA") }
    }
    paragraph(doc, ParagraphAlignment.CENTER, spacingAfter = 200) {
        run(size = 9, color = "666666") {
            setText("10 avenue des Champs-Élysées, 75008 Paris — SIREN 123 456 789 — RCS Paris")
        }
    }

    // Séparateur
    doc.createParagraph().apply {
        borderBottom = Borders.SINGLE
        spacingAfter = 400
    }

    // ── Titre ──
    paragraph(doc, ParagraphAlignment.CENTER, spacingAfter = 400) {
        run(bold = true, size = 14) { setText("ATTESTATION D'ASSURANCE") }
    }

    // ── Champs dans un tableau sans bordure (alignement propre) ──
    createFieldsTable(doc)

    spacer(doc, 300)

    // ── Tableau récapitulatif statique ──
    paragraph(doc, spacingAfter = 120) {
        run(bold = true, size = 11) { setText("Récapitulatif du contrat") }
    }
    createStaticTable(doc)

    spacer(doc, 300)

    // ── Corps de l'attestation ──
    paragraph(doc, ParagraphAlignment.BOTH, spacingAfter = 300, lineSpacing = 1.15) {
        run(size = 10) {
            setText(
                "Nous certifions par la présente que {{customerName}}, " +
                    "domicilié(e) au {{address}}, est titulaire du contrat d'assurance " +
                    "n° {{policyNumber}} auprès de notre compagnie. " +
                    "Ce contrat est en vigueur du {{startDate}} au {{endDate}}, " +
                    "pour une prime annuelle de {{premium}}."
            )
        }
    }

    // ── Tableau dynamique : mouvements ──
    paragraph(doc, spacingAfter = 120) {
        run(bold = true, size = 11) { setText("Derniers mouvements du contrat") }
    }
    createDynamicTable(doc)

    spacer(doc, 400)

    // ── Signature ──
    paragraph(doc, ParagraphAlignment.RIGHT) {
        run(size = 10) { setText("Fait à Paris, le {{startDate}}") }
    }
    spacer(doc, 200)
    paragraph(doc, ParagraphAlignment.RIGHT) {
        run(bold = true, size = 10) { setText("Le Directeur Général") }
    }

    // ── Sauvegarde ──
    val dir = Paths.get(outputDir)
    Files.createDirectories(dir)
    FileOutputStream(dir.resolve("attestation-assurance.docx").toFile()).use { doc.write(it) }
    doc.close()
}

// ── Helpers ──────────────────────────────────────────────────────────────

private fun setupPageFormat(doc: XWPFDocument) {
    val sectPr = doc.document.body.addNewSectPr()
    val pgSz = sectPr.addNewPgSz()
    pgSz.w = BigInteger.valueOf(11906)
    pgSz.h = BigInteger.valueOf(16838)
    pgSz.orient = STPageOrientation.PORTRAIT
    val pgMar = sectPr.addNewPgMar()
    pgMar.top = BigInteger.valueOf(1134)    // ~2cm
    pgMar.bottom = BigInteger.valueOf(1134)
    pgMar.left = BigInteger.valueOf(1134)
    pgMar.right = BigInteger.valueOf(1134)
}

private fun paragraph(
    doc: XWPFDocument,
    alignment: ParagraphAlignment = ParagraphAlignment.LEFT,
    spacingBefore: Int = 0,
    spacingAfter: Int = 0,
    lineSpacing: Double = 0.0,
    block: XWPFParagraph.() -> Unit
): XWPFParagraph {
    return doc.createParagraph().apply {
        this.alignment = alignment
        if (spacingBefore > 0) this.spacingBefore = spacingBefore
        if (spacingAfter > 0) this.spacingAfter = spacingAfter
        if (lineSpacing > 0) this.spacingBetween = lineSpacing
        block()
    }
}

private fun XWPFParagraph.run(
    bold: Boolean = false,
    size: Int = 10,
    color: String? = null,
    block: XWPFRun.() -> Unit
): XWPFRun {
    return createRun().apply {
        isBold = bold
        fontSize = size
        fontFamily = "Arial"
        color?.let { this.color = it }
        block()
    }
}

private fun spacer(doc: XWPFDocument, twips: Int) {
    doc.createParagraph().apply { spacingAfter = twips }
}

// ── Tableau de champs (sans bordure, 2 colonnes label/valeur) ────────────

/**
 * Utilise un tableau invisible pour aligner proprement les champs label/valeur.
 * Chaque champ est sur sa propre ligne de tableau → pas de chevauchement.
 */
private fun createFieldsTable(doc: XWPFDocument) {
    val fields = listOf(
        "Nom de l'assuré" to "{{customerName}}",
        "Numéro de police" to "{{policyNumber}}",
        "Adresse" to "{{address}}",
        "Date d'effet" to "{{startDate}}",
        "Date d'expiration" to "{{endDate}}",
        "Prime annuelle" to "{{premium}}"
    )

    val table = doc.createTable(fields.size, 2).apply { width = 9000 }
    ensureTableGrid(table, listOf(3000L, 6000L))

    // Supprimer toutes les bordures (tableau invisible)
    table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
    table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
    table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
    table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
    table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
    table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")

    for ((row, field) in fields.withIndex()) {
        setFieldCell(table, row, 0, "${field.first} :", bold = true)
        setFieldCell(table, row, 1, field.second, bold = false)
    }
}

private fun setFieldCell(table: XWPFTable, row: Int, col: Int, text: String, bold: Boolean) {
    val cell = table.getRow(row).getCell(col)
    val para = cell.paragraphs[0].apply {
        spacingBefore = 40
        spacingAfter = 40
    }
    para.createRun().apply {
        isBold = bold
        fontSize = 10
        fontFamily = "Arial"
        setText(text)
    }
}

// ── Tableau statique (récapitulatif) ─────────────────────────────────────

private fun createStaticTable(doc: XWPFDocument) {
    val table = doc.createTable(4, 2).apply { width = 9000 }
    ensureTableGrid(table, listOf(3000L, 6000L))
    styleTableBorders(table)

    setCell(table, 0, 0, "Rubrique", bold = true, bg = "E8E8E8")
    setCell(table, 0, 1, "Détail", bold = true, bg = "E8E8E8")
    setCell(table, 1, 0, "Assuré", bold = true)
    setCell(table, 1, 1, "{{customerName}}")
    setCell(table, 2, 0, "Police", bold = true)
    setCell(table, 2, 1, "{{policyNumber}}")
    setCell(table, 3, 0, "Période", bold = true)
    setCell(table, 3, 1, "Du {{startDate}} au {{endDate}}")
}

// ── Tableau dynamique (mouvements) ───────────────────────────────────────

private fun createDynamicTable(doc: XWPFDocument) {
    val table = doc.createTable(2, 4).apply { width = 9000 }
    ensureTableGrid(table, listOf(1600L, 3600L, 1900L, 1900L))
    styleTableBorders(table)

    val headers = listOf("Date", "Libellé", "Montant", "Solde")
    for ((col, header) in headers.withIndex()) {
        setCell(table, 0, col, header, bold = true, bg = "E8E8E8")
    }

    // Ligne-modèle avec marqueur
    setCell(table, 1, 0, "{{#movements}}")
    for (col in 1..3) {
        setCell(table, 1, col, "")
    }
}

// ── Utilitaires tableaux ─────────────────────────────────────────────────

private fun setCell(
    table: XWPFTable,
    row: Int,
    col: Int,
    text: String,
    bold: Boolean = false,
    bg: String? = null
) {
    val cell = table.getRow(row).getCell(col)
    bg?.let { cell.color = it }

    val para = cell.paragraphs[0].apply {
        spacingBefore = 60
        spacingAfter = 60
    }
    para.createRun().apply {
        isBold = bold
        fontSize = 9
        fontFamily = "Arial"
        setText(text)
    }
}

private fun styleTableBorders(table: XWPFTable) {
    table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "CCCCCC")
    table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "CCCCCC")
    table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "999999")
    table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "999999")
    table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "999999")
    table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "999999")
}

private fun ensureTableGrid(table: XWPFTable, colWidths: List<Long>) {
    val ctTblGrid = table.ctTbl.let { ctTbl ->
        if (ctTbl.tblGrid == null) ctTbl.addNewTblGrid() else ctTbl.tblGrid
    }
    if (ctTblGrid.gridColList.isEmpty()) {
        for (width in colWidths) {
            ctTblGrid.addNewGridCol().w = BigInteger.valueOf(width)
        }
    }
}
