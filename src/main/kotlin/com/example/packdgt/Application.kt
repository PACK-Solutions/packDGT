package com.example.packdgt

import com.example.packdgt.api.plugins.*
import com.example.packdgt.api.routes.documentRoutes
import com.example.packdgt.api.routes.uiRoutes
import com.example.packdgt.config.AppConfig
import com.example.packdgt.service.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

const val SERVER_PORT = 8080

fun main() {
    embeddedServer(Netty, port = SERVER_PORT) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")
    val config = AppConfig.load(environment)

    logger.info("Démarrage de packDGT")
    logger.info("Templates : {}", config.templatesDirectory)
    logger.info("Sortie : {} (sauvegarde={})", config.outputDirectory, config.saveToDisc)
    logger.info("UI disponible : http://localhost:{}/ui", SERVER_PORT)

    // Plugins Ktor
    configureSerialization()
    configureCallId()
    configureCallLogging()
    configureStatusPages()

    // Services
    val documentStoreService = DocumentStoreService()
    val templateService = TemplateService(config.templatesDirectory)
    val pdfConversionService = PdfConversionService(
        poolSize = config.libreOfficePoolSize,
        startPort = config.libreOfficePort
    )
    val pdfPostProcessingService = PdfPostProcessingService()
    val documentGenerationService = DocumentGenerationService(
        appConfig = config,
        templateService = templateService,
        pdfConversionService = pdfConversionService,
        pdfPostProcessingService = pdfPostProcessingService
    )

    // Démarrer le pool LibreOffice
    pdfConversionService.start()

    // Arrêt propre du pool LibreOffice à l'arrêt de l'application
    monitor.subscribe(ApplicationStopped) {
        pdfConversionService.stop()
    }

    // Éviction périodique des documents expirés (toutes les 5 min)
    launch {
        while (isActive) {
            delay(5 * 60 * 1000L)
            documentStoreService.evictExpired()
        }
    }

    // Routes
    routing {
        documentRoutes(documentGenerationService, templateService)
        uiRoutes(documentGenerationService, documentStoreService)
        staticResources("/ui", "static") {
            default("index.html")
        }
    }
}
