package com.example.packdgt

import com.example.packdgt.api.plugins.*
import com.example.packdgt.api.routes.documentRoutes
import com.example.packdgt.config.AppConfig
import com.example.packdgt.service.DocumentGenerationService
import com.example.packdgt.service.PdfConversionService
import com.example.packdgt.service.PdfPostProcessingService
import com.example.packdgt.service.TemplateService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")
    val config = AppConfig.load(environment)

    logger.info("Démarrage de packDGT")
    logger.info("Templates : {}", config.templatesDirectory)
    logger.info("Sortie : {} (sauvegarde={})", config.outputDirectory, config.saveToDisc)

    // Plugins Ktor
    configureSerialization()
    configureCallId()
    configureCallLogging()
    configureStatusPages()

    // Services (composition manuelle — pas de framework DI pour rester simple)
    val templateService = TemplateService(config.templatesDirectory)
    val pdfConversionService = PdfConversionService()
    val pdfPostProcessingService = PdfPostProcessingService()
    val documentGenerationService = DocumentGenerationService(
        appConfig = config,
        templateService = templateService,
        pdfConversionService = pdfConversionService,
        pdfPostProcessingService = pdfPostProcessingService
    )

    // Routes
    routing {
        documentRoutes(documentGenerationService, templateService)
    }
}
