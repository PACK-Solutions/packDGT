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
