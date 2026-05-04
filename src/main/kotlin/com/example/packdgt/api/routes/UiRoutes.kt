package com.example.packdgt.api.routes

import com.example.packdgt.api.dto.AppendTextRequest
import com.example.packdgt.api.dto.AppendTextResponse
import com.example.packdgt.api.dto.GenerateRequest
import com.example.packdgt.api.dto.GenerateResponse
import com.example.packdgt.config.TemplateRegistry
import com.example.packdgt.exception.DocumentNotFoundException
import com.example.packdgt.exception.InvalidRequestException
import com.example.packdgt.service.DocumentGenerationService
import com.example.packdgt.service.DocumentStoreService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader

fun Route.uiRoutes(
    documentGenerationService: DocumentGenerationService,
    documentStoreService: DocumentStoreService
) {
    route("/api") {

        get("/templates") {
            val templates = TemplateRegistry.getAll()
            call.respond(mapOf("templates" to templates))
        }

        route("/documents") {

            post("/generate") {
                val request = call.receive<GenerateRequest>()
                val result = documentGenerationService.generate(request, freeTextPlaceholder = true)
                val id = documentStoreService.store(result.fileName, result.pdfBytes, originalRequest = request)
                call.respond(HttpStatusCode.Created, GenerateResponse(id, result.fileName))
            }

            get("/{id}/pdf") {
                val id = call.parameters["id"]!!
                val doc = documentStoreService.get(id) ?: throw DocumentNotFoundException(id)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Inline.withParameter(
                        ContentDisposition.Parameters.FileName, doc.fileName
                    ).toString()
                )
                call.respondBytes(
                    bytes = doc.pdfBytes,
                    contentType = ContentType.Application.Pdf
                )
            }

            get("/{id}/download") {
                val id = call.parameters["id"]!!
                val doc = documentStoreService.get(id) ?: throw DocumentNotFoundException(id)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName, doc.fileName
                    ).toString()
                )
                call.respondBytes(
                    bytes = doc.pdfBytes,
                    contentType = ContentType.Application.Pdf
                )
            }

            post("/{id}/append-text") {
                val id = call.parameters["id"]!!
                val doc = documentStoreService.get(id) ?: throw DocumentNotFoundException(id)
                val request = call.receive<AppendTextRequest>()
                val originalRequest = doc.originalRequest
                    ?: throw InvalidRequestException("Données originales non disponibles pour la régénération")

                val result = documentGenerationService.generate(originalRequest, freeText = request.text)
                documentStoreService.update(id, result.pdfBytes)

                val pageCount = Loader.loadPDF(result.pdfBytes).use { it.numberOfPages }
                call.respond(AppendTextResponse(id, doc.fileName, pageCount))
            }

            post("/{id}/finalize") {
                val id = call.parameters["id"]!!
                val doc = documentStoreService.get(id) ?: throw DocumentNotFoundException(id)
                val originalRequest = doc.originalRequest
                    ?: throw InvalidRequestException("Données originales non disponibles pour la régénération")

                val result = documentGenerationService.generate(originalRequest)
                documentStoreService.update(id, result.pdfBytes)

                call.respond(mapOf("id" to id, "fileName" to doc.fileName))
            }
        }
    }
}
