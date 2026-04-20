package com.example.packdgt.config

import io.ktor.server.application.*

data class AppConfig(
    val templatesDirectory: String,
    val outputDirectory: String,
    val saveToDisc: Boolean,
    val libreOfficePort: Int = 2002,
    val libreOfficePoolSize: Int = 2
) {
    companion object {
        fun load(environment: ApplicationEnvironment): AppConfig {
            val config = environment.config
            return AppConfig(
                templatesDirectory = config.propertyOrNull("app.templates.directory")?.getString() ?: "templates",
                outputDirectory = config.propertyOrNull("app.output.directory")?.getString() ?: "output",
                saveToDisc = config.propertyOrNull("app.output.saveToDisc")?.getString()?.toBoolean() ?: false,
                libreOfficePort = config.propertyOrNull("app.libreoffice.port")?.getString()?.toInt() ?: 2002,
                libreOfficePoolSize = config.propertyOrNull("app.libreoffice.poolSize")?.getString()?.toInt() ?: 2
            )
        }
    }
}
