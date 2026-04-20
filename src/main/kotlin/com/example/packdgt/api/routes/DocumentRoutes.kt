package com.example.packdgt.api.routes

import com.example.packdgt.api.dto.GenerateRequest
import com.example.packdgt.api.dto.HealthResponse
import com.example.packdgt.service.DocumentGenerationService
import com.example.packdgt.service.TemplateService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.documentRoutes(
    documentGenerationService: DocumentGenerationService,
    templateService: TemplateService
) {
    get("/health") {
        val templates = templateService.listTemplates()
        call.respond(
            HealthResponse(
                status = "UP",
                templatesCount = templates.size
            )
        )
    }

    get("/templates") {
        val templates = templateService.listTemplates()
        call.respond(mapOf("templates" to templates))
    }

    post("/generate") {
        val request = call.receive<GenerateRequest>()
        val result = documentGenerationService.generate(request)

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, result.fileName
            ).toString()
        )
        call.respondBytes(
            bytes = result.pdfBytes,
            contentType = ContentType.Application.Pdf,
            status = HttpStatusCode.OK
        )
    }
}
