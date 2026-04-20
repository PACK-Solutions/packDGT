package com.example.packdgt.exception

sealed class AppException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class TemplateNotFoundException(templateName: String) :
    AppException("Template introuvable : $templateName")

class TemplateProcessingException(message: String, cause: Throwable? = null) :
    AppException("Erreur de traitement du template : $message", cause)

class PdfConversionException(message: String, cause: Throwable? = null) :
    AppException("Erreur de conversion DOCX vers PDF : $message", cause)

class PdfPostProcessingException(message: String, cause: Throwable? = null) :
    AppException("Erreur de post-traitement PDF : $message", cause)

class InvalidRequestException(message: String) :
    AppException(message)
