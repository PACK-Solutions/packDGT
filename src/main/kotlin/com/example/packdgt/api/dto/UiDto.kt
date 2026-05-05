package com.example.packdgt.api.dto

data class TemplateMetadata(
    val name: String,
    val label: String,
    val fields: List<FieldDef>,
    val tables: List<TableDef>
)

data class FieldDef(
    val key: String,
    val label: String,
    val placeholder: String,
    val group: String? = null
)

data class TableDef(
    val key: String,
    val label: String,
    val columns: List<String>
)

data class GenerateResponse(
    val id: String,
    val fileName: String
)

data class AppendTextRequest(
    val text: String
)

data class AppendTextResponse(
    val id: String,
    val fileName: String,
    val pageCount: Int
)

data class BatchGenerateRequest(
    val templateName: String,
    val count: Int,
    val outputFileName: String? = null,
    val data: Map<String, String> = emptyMap(),
    val tables: Map<String, List<List<String>>> = emptyMap(),
    val options: com.example.packdgt.api.dto.GenerateOptions? = null
)

data class BatchGenerateResponse(
    val id: String,
    val fileName: String,
    val count: Int,
    val totalMs: Long,
    val sizeBytes: Int
)
