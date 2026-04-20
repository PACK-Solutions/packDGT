---
name: ktor
description: Ktor 3.x patterns, routing, plugins, testing - use when adding endpoints, configuring plugins, or writing integration tests
---

# Ktor 3.x — Conventions packDGT

## Version

Ktor **3.0.3** avec Kotlin 2.1.0, Netty, Jackson.

## Architecture

- Point d'entrée : `Application.kt` — `embeddedServer(Netty, port = 8080) { module() }`
- Plugins configurés dans `api/plugins/Plugins.kt` via extensions `Application.configure*()`
- Routes définies dans `api/routes/` comme extensions `Route.xxxRoutes()`
- Configuration YAML dans `src/main/resources/application.yaml`

## Ajouter un endpoint

1. Créer ou modifier un fichier dans `api/routes/`
2. Définir une extension `Route.xxxRoutes(service: XxxService)` — injection manuelle, pas de framework DI
3. Enregistrer dans `Application.module()` dans le bloc `routing { }`
4. Les DTOs vont dans `api/dto/Dto.kt`

```kotlin
// api/routes/DocumentRoutes.kt
fun Route.documentRoutes(service: DocumentGenerationService, templateService: TemplateService) {
    post("/generate") {
        val request = call.receive<GenerateRequest>()
        val result = service.generate(request)
        call.respondBytes(result.pdfBytes, ContentType.Application.Pdf)
    }
}
```

## Plugins installés

| Plugin | Rôle |
|--------|------|
| `ContentNegotiation` + Jackson | Sérialisation JSON |
| `CallId` | Correlation ID (header `X-Request-Id` ou auto-généré) |
| `CallLogging` | Logs HTTP avec MDC `correlationId` |
| `StatusPages` | Mapping exceptions → codes HTTP |

## Gestion d'erreurs

Hiérarchie `sealed class AppException` dans `exception/AppExceptions.kt`.
Mapping dans `StatusPages` :
- `TemplateNotFoundException` → 404
- `InvalidRequestException` → 400
- `TemplateProcessingException` → 422
- `PdfConversionException` / `PdfPostProcessingException` → 500
- `Throwable` → 500 (catch-all)

## Écrire un test d'intégration Ktor

```kotlin
private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    environment {
        config = MapApplicationConfig(
            "app.templates.directory" to "templates",
            "app.output.directory" to "build/test-output",
            "app.output.saveToDisc" to "false"
        )
    }
    application { module() }
    block()
}

@Test
fun `test endpoint`() = testApp {
    val client = createClient { install(ContentNegotiation) { jackson() } }
    val response = client.get("/health")
    assertEquals(HttpStatusCode.OK, response.status)
}
```

Points d'attention :
- Utiliser `MapApplicationConfig` pour éviter le chargement automatique du module depuis YAML
- Appeler `application { module() }` explicitement
- Créer un client JSON avec `createClient { install(ContentNegotiation) { jackson() } }`

## Ktor 2 → 3 — Changements clés

- Artifacts : `-jvm` suffix supprimé (`ktor-server-core` au lieu de `ktor-server-core-jvm`)
- Config : `ktor-server-config-yaml` remplace HOCON par défaut
- CallLogging : `io.ktor.server.plugins.calllogging` (corrigé le typo `callloging`)
- `HoconApplicationConfig` → `MapApplicationConfig` pour les tests
- `response.readBytes()` → `response.readRawBytes()`
- `embeddedServer` : builder simplifié `embeddedServer(Netty, port = 8080) { module() }`
