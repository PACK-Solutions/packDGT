package com.example.packdgt.api

import com.example.packdgt.api.dto.ErrorResponse
import com.example.packdgt.api.dto.HealthResponse
import com.example.packdgt.module
import com.example.packdgt.tools.createSampleTemplate
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentRoutesTest {

    @BeforeAll
    fun setup() {
        createSampleTemplate("templates")
    }

    private fun jsonClient(builder: ApplicationTestBuilder) = builder.createClient {
        install(ContentNegotiation) { jackson() }
    }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "app.templates.directory" to "templates",
                "app.output.directory" to "build/test-output",
                "app.output.saveToDisc" to "false"
            )
        }
        application { module() }
        block()
    }

    @Test
    fun `GET health retourne UP`() = testApp {
        val client = jsonClient(this)
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val health = response.body<HealthResponse>()
        assertEquals("UP", health.status)
    }

    @Test
    fun `GET templates retourne la liste des templates`() = testApp {
        val client = jsonClient(this)
        val response = client.get("/templates")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<Map<String, List<String>>>()
        assertTrue(body["templates"]!!.contains("attestation-assurance.docx"))
    }

    @Test
    fun `POST generate retourne un PDF`() = testApp {
        val client = jsonClient(this)
        val response = client.post("/generate") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "templateName" to "attestation-assurance.docx",
                    "outputFileName" to "test.pdf",
                    "data" to mapOf(
                        "customerName" to "Test Integration",
                        "policyNumber" to "POL-INT-001",
                        "startDate" to "2026-04-01",
                        "endDate" to "2027-03-31",
                        "premium" to "999 €",
                        "address" to "42 rue Test"
                    ),
                    "tables" to mapOf(
                        "movements" to listOf(
                            listOf("01/04/2026", "Prélèvement prime", "-125,05 €", "8 432,50 €"),
                            listOf("15/03/2026", "Remboursement", "+2 500,00 €", "8 557,55 €")
                        )
                    ),
                    "options" to mapOf(
                        "watermark" to "TEST",
                        "author" to "Test Suite",
                        "title" to "Test Document"
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())

        val pdfBytes = response.readRawBytes()
        assertTrue(pdfBytes.size > 100)
        assertTrue(String(pdfBytes.take(5).toByteArray()).startsWith("%PDF"))

        val disposition = response.headers[HttpHeaders.ContentDisposition]
        assertNotNull(disposition)
        assertTrue(disposition!!.contains("test.pdf"))
    }

    @Test
    fun `POST generate avec template inexistant retourne 404`() = testApp {
        val client = jsonClient(this)
        val response = client.post("/generate") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "templateName" to "inexistant.docx",
                    "data" to emptyMap<String, String>()
                )
            )
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals(404, error.status)
        assertTrue(error.message.contains("introuvable"))
    }

    @Test
    fun `POST generate avec templateName vide retourne 400`() = testApp {
        val client = jsonClient(this)
        val response = client.post("/generate") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "templateName" to "",
                    "data" to emptyMap<String, String>()
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST generate avec extension invalide retourne 400`() = testApp {
        val client = jsonClient(this)
        val response = client.post("/generate") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "templateName" to "template.txt",
                    "data" to emptyMap<String, String>()
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
