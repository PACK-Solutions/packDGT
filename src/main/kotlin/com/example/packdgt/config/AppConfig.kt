package com.example.packdgt.config

import io.ktor.server.application.*

data class AppConfig(
    val templatesDirectory: String,
    val outputDirectory: String,
    val saveToDisc: Boolean
) {
    companion object {
        fun load(environment: ApplicationEnvironment): AppConfig {
            val config = environment.config
            return AppConfig(
                templatesDirectory = config.propertyOrNull("app.templates.directory")?.getString() ?: "templates",
                outputDirectory = config.propertyOrNull("app.output.directory")?.getString() ?: "output",
                saveToDisc = config.propertyOrNull("app.output.saveToDisc")?.getString()?.toBoolean() ?: false
            )
        }

        fun fromMap(map: Map<String, String>): AppConfig {
            return AppConfig(
                templatesDirectory = map["app.templates.directory"] ?: "templates",
                outputDirectory = map["app.output.directory"] ?: "output",
                saveToDisc = map["app.output.saveToDisc"]?.toBoolean() ?: false
            )
        }
    }
}
