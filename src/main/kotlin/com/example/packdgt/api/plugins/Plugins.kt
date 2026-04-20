package com.example.packdgt.api.plugins

import com.example.packdgt.api.dto.ErrorResponse
import com.example.packdgt.exception.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.*

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
}

fun Application.configureCallId() {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString().substring(0, 8) }
        verify { it.isNotEmpty() }
    }
}

fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        callIdMdc("correlationId")
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            "$method $uri -> $status"
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<TemplateNotFoundException> { call, cause ->
            val correlationId = call.callId
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    status = 404,
                    error = "Not Found",
                    message = cause.message ?: "Template introuvable",
                    correlationId = correlationId
                )
            )
        }

        exception<InvalidRequestException> { call, cause ->
            val correlationId = call.callId
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    status = 400,
                    error = "Bad Request",
                    message = cause.message ?: "Requête invalide",
                    correlationId = correlationId
                )
            )
        }

        exception<TemplateProcessingException> { call, cause ->
            val correlationId = call.callId
            logger.error("[{}] Erreur de traitement template", correlationId, cause)
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse(
                    status = 422,
                    error = "Unprocessable Entity",
                    message = cause.message ?: "Erreur de traitement du template",
                    correlationId = correlationId
                )
            )
        }

        exception<PdfConversionException> { call, cause ->
            val correlationId = call.callId
            logger.error("[{}] Erreur de conversion PDF", correlationId, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    status = 500,
                    error = "Internal Server Error",
                    message = "Erreur lors de la conversion en PDF",
                    correlationId = correlationId
                )
            )
        }

        exception<PdfPostProcessingException> { call, cause ->
            val correlationId = call.callId
            logger.error("[{}] Erreur de post-traitement PDF", correlationId, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    status = 500,
                    error = "Internal Server Error",
                    message = "Erreur lors du post-traitement PDF",
                    correlationId = correlationId
                )
            )
        }

        exception<Throwable> { call, cause ->
            val correlationId = call.callId
            logger.error("[{}] Erreur inattendue", correlationId, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    status = 500,
                    error = "Internal Server Error",
                    message = "Erreur interne du serveur",
                    correlationId = correlationId
                )
            )
        }
    }
}
