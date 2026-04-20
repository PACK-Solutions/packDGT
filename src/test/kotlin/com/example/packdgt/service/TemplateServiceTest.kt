package com.example.packdgt.service

import com.example.packdgt.exception.TemplateNotFoundException
import com.example.packdgt.tools.createSampleTemplate
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateServiceTest {

    private val testTemplatesDir = "build/test-templates"
    private lateinit var service: TemplateService

    @BeforeAll
    fun setup() {
        createSampleTemplate(testTemplatesDir)
        service = TemplateService(testTemplatesDir)
    }

    @Test
    fun `listTemplates retourne les fichiers docx`() {
        val templates = service.listTemplates()
        assertTrue(templates.contains("attestation-assurance.docx"))
    }

    @Test
    fun `templateExists retourne true pour un template existant`() {
        assertTrue(service.templateExists("attestation-assurance.docx"))
    }

    @Test
    fun `templateExists retourne false pour un template inexistant`() {
        assertFalse(service.templateExists("inexistant.docx"))
    }

    @Test
    fun `process remplace les placeholders dans le document`() {
        val data = mapOf(
            "customerName" to "Jean Dupont",
            "policyNumber" to "POL-2026-000123",
            "startDate" to "2026-04-01",
            "endDate" to "2027-03-31",
            "premium" to "1250,50 €",
            "address" to "10 rue Exemple, 75001 Paris"
        )

        val result = service.process("attestation-assurance.docx", data)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())

        val doc = XWPFDocument(ByteArrayInputStream(result))

        // Les champs sont dans un tableau sans bordure (index 0), vérifier les valeurs
        val allText = buildString {
            doc.paragraphs.forEach { append(it.text).append(" ") }
            doc.tables.forEach { t ->
                t.rows.forEach { r ->
                    r.tableCells.forEach { c ->
                        c.paragraphs.forEach { append(it.text).append(" ") }
                    }
                }
            }
        }

        assertTrue(allText.contains("Jean Dupont"), "Le nom du client doit apparaître")
        assertTrue(allText.contains("POL-2026-000123"), "Le numéro de police doit apparaître")
        assertTrue(allText.contains("1250,50 €"), "La prime doit apparaître")
        assertFalse(allText.contains("{{customerName}}"), "Le placeholder ne doit plus être présent")
        assertFalse(allText.contains("{{policyNumber}}"), "Le placeholder ne doit plus être présent")

        doc.close()
    }

    @Test
    fun `process remplace les placeholders dans les tableaux statiques`() {
        val data = mapOf(
            "customerName" to "Marie Martin",
            "policyNumber" to "POL-2026-999",
            "startDate" to "2026-01-01",
            "endDate" to "2026-12-31",
            "premium" to "800 €",
            "address" to "5 rue Test"
        )

        val result = service.process("attestation-assurance.docx", data)
        val doc = XWPFDocument(ByteArrayInputStream(result))

        // Table index: 0=champs, 1=récapitulatif, 2=mouvements
        val staticTable = doc.tables[1]
        val tableTexts = staticTable.rows.flatMap { row ->
            row.tableCells.map { cell ->
                cell.paragraphs.joinToString(" ") { it.text }
            }
        }
        val fullTableText = tableTexts.joinToString(" ")

        assertTrue(fullTableText.contains("Marie Martin"))
        assertTrue(fullTableText.contains("POL-2026-999"))
        assertFalse(fullTableText.contains("{{customerName}}"))

        doc.close()
    }

    @Test
    fun `process expanse le tableau dynamique movements`() {
        val data = mapOf("customerName" to "Test Dynamique")
        val tables = mapOf(
            "movements" to listOf(
                listOf("01/04/2026", "Prélèvement prime", "-125,05 €", "8 432,50 €"),
                listOf("15/03/2026", "Remboursement sinistre", "+2 500,00 €", "8 557,55 €"),
                listOf("01/03/2026", "Prélèvement prime", "-125,05 €", "6 057,55 €")
            )
        )

        val result = service.process("attestation-assurance.docx", data, tables)
        val doc = XWPFDocument(ByteArrayInputStream(result))

        // Table index: 0=champs, 1=récapitulatif, 2=mouvements
        val dynamicTable = doc.tables[2]

        // 1 ligne d'en-tête + 3 lignes de données = 4 lignes
        assertEquals(4, dynamicTable.rows.size, "Le tableau doit avoir 4 lignes (1 header + 3 data)")

        // Vérifier les en-têtes
        val headerRow = dynamicTable.getRow(0)
        assertEquals("Date", headerRow.getCell(0).text)
        assertEquals("Libellé", headerRow.getCell(1).text)
        assertEquals("Montant", headerRow.getCell(2).text)
        assertEquals("Solde", headerRow.getCell(3).text)

        // Vérifier la première ligne de données
        val firstDataRow = dynamicTable.getRow(1)
        assertEquals("01/04/2026", firstDataRow.getCell(0).text)
        assertEquals("Prélèvement prime", firstDataRow.getCell(1).text)
        assertEquals("-125,05 €", firstDataRow.getCell(2).text)
        assertEquals("8 432,50 €", firstDataRow.getCell(3).text)

        // Vérifier la dernière ligne de données
        val lastDataRow = dynamicTable.getRow(3)
        assertEquals("01/03/2026", lastDataRow.getCell(0).text)

        // Le marqueur {{#movements}} ne doit plus être présent
        val allText = dynamicTable.rows.flatMap { r ->
            r.tableCells.map { it.text }
        }.joinToString(" ")
        assertFalse(allText.contains("{{#movements}}"), "Le marqueur doit avoir été supprimé")

        doc.close()
    }

    @Test
    fun `process gère un tableau dynamique vide`() {
        val data = mapOf("customerName" to "Test Vide")
        val tables = mapOf("movements" to emptyList<List<String>>())

        val result = service.process("attestation-assurance.docx", data, tables)
        val doc = XWPFDocument(ByteArrayInputStream(result))

        val dynamicTable = doc.tables[2]
        assertEquals(1, dynamicTable.rows.size, "Le tableau vide ne doit avoir que l'en-tête")
        assertEquals("Date", dynamicTable.getRow(0).getCell(0).text)

        doc.close()
    }

    @Test
    fun `process ignore les tables non référencées dans le JSON`() {
        val data = mapOf("customerName" to "Test Ignore")
        // Pas de clé "movements" dans tables
        val tables = mapOf("autreTableau" to listOf(listOf("a", "b")))

        val result = service.process("attestation-assurance.docx", data, tables)
        val doc = XWPFDocument(ByteArrayInputStream(result))

        val dynamicTable = doc.tables[2]
        assertEquals(2, dynamicTable.rows.size, "Le tableau non référencé garde ses 2 lignes")

        doc.close()
    }

    @Test
    fun `process lève TemplateNotFoundException pour un template inexistant`() {
        assertThrows<TemplateNotFoundException> {
            service.process("inexistant.docx", emptyMap())
        }
    }

    @Test
    fun `process empêche le path traversal`() {
        assertThrows<TemplateNotFoundException> {
            service.process("../../../etc/passwd", emptyMap())
        }
    }

    @Test
    fun `process fonctionne avec des données partielles`() {
        val data = mapOf("customerName" to "Test Partiel")
        val result = service.process("attestation-assurance.docx", data)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())

        val doc = XWPFDocument(ByteArrayInputStream(result))
        val fullText = doc.paragraphs.joinToString(" ") { it.text }
        assertTrue(fullText.contains("Test Partiel"))
        assertTrue(fullText.contains("{{policyNumber}}"), "Les placeholders non fournis restent")
        doc.close()
    }

    @AfterAll
    fun cleanup() {
        val dir = Paths.get(testTemplatesDir)
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
