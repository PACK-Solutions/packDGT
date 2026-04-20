package com.example.packdgt.api.dto

import com.fasterxml.jackson.annotation.JsonInclude

data class GenerateRequest(
    val templateName: String,
    val outputFileName: String? = null,
    val data: Map<String, String> = emptyMap(),
    val tables: Map<String, List<List<String>>> = emptyMap(),
    val options: GenerateOptions? = null
)

data class GenerateOptions(
    val watermark: String? = null,
    val author: String? = null,
    val title: String? = null,
    val subject: String? = null,
    val protect: Boolean = false,
    val saveToDisc: Boolean? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val correlationId: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class HealthResponse(
    val status: String = "UP",
    val version: String = "1.0.0",
    val templatesDirectory: String? = null,
    val templatesCount: Int? = null
)
