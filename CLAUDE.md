# packDGT - Document Generation Tool

API REST de generation de PDF a partir de templates Word/DOCX et de donnees JSON.

## Stack technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| Langage | Kotlin | 2.1.0 |
| Framework HTTP | Ktor | 3.0.3 |
| Build | Gradle (Kotlin DSL) | 8.12 |
| JVM | Java | 21 |
| DOCX manipulation | Apache POI (XWPF) | 5.3.0 |
| DOCX -> PDF | JODConverter + LibreOffice | 4.4.7 |
| PDF post-processing | Apache PDFBox | 3.0.3 |
| JSON | Jackson | 2.18.1 |
| Logging | Logback + SLF4J | 1.5.11 |
| Tests | JUnit 5 + Ktor Test Host | 5.11.3 |

## Pre-requis

LibreOffice installe et `soffice` dans le PATH : `brew install --cask libreoffice`

## Architecture

```
src/main/kotlin/com/example/packdgt/
├── Application.kt              # Point d'entree, module Ktor, cycle de vie LO pool
├── config/AppConfig.kt         # Configuration YAML -> data class
├── exception/AppExceptions.kt  # Hierarchie sealed d'exceptions metier
├── api/
│   ├── dto/Dto.kt              # GenerateRequest, GenerateOptions, ErrorResponse, HealthResponse
│   ├── plugins/Plugins.kt      # ContentNegotiation, CallId, CallLogging, StatusPages
│   └── routes/DocumentRoutes.kt # GET /health, GET /templates, POST /generate
├── service/
│   ├── DocumentGenerationService.kt  # Orchestrateur du pipeline (avec metriques)
│   ├── TemplateService.kt            # POI : placeholders + tableaux + cache memoire
│   ├── PdfConversionService.kt       # JODConverter : pool LibreOffice resident (UNO socket)
│   └── PdfPostProcessingService.kt   # PDFBox : watermark, metadonnees, pagination, protection
└── tools/TemplateGenerator.kt  # Utilitaire creation template DOCX d'exemple
```

### Pipeline de generation (~70ms en regime permanent)

```
JSON request -> Validation -> POI (placeholders + tables) [~10ms] -> JODConverter/LO (DOCX->PDF) [~57ms] -> PDFBox (post-processing) [~3ms] -> HTTP response
```

## Commandes

```bash
./gradlew build          # Compile + tests
./gradlew test           # Tests uniquement
./gradlew run            # Lancer le serveur sur :8080
```

## Endpoints

- `GET /health` — healthcheck, retourne `{ status: "UP", templatesCount: N }`
- `GET /templates` — liste des templates DOCX disponibles
- `POST /generate` — genere un PDF, retourne le binaire (Content-Type: application/pdf)

## Conventions

- Placeholders DOCX simples : `{{nomDuChamp}}` (camelCase)
- Tableaux dynamiques : marqueur `{{#tableName}}` dans la 1re cellule de la ligne-modele
- Le champ `tables` du JSON contient les donnees tabulaires : `Map<String, List<List<String>>>`
- Templates stockes dans `templates/` (configurable via `TEMPLATES_DIR`)
- Fichiers `.http` IntelliJ dans `http/` pour tester l'API
- Gestion d'erreurs via `sealed class AppException` -> StatusPages mapping HTTP
- Correlation ID automatique dans les logs (header `X-Request-Id` ou auto-genere)
- DI manuelle (pas de framework), services instancies dans `Application.module()`

## Optimisations performance

- **Pool LibreOffice resident** (JODConverter) : instances soffice en memoire, socket UNO — pas de cold-start par requete
- **Cache template** : `ConcurrentHashMap` avec invalidation sur `lastModified`
- **Fonts PDFBox pre-instanciees** : `PDType1Font` crees une fois, reutilises (thread-safe)
- **Metriques de timing** : chaque etape chronometree dans les logs

## Contraintes connues

- LibreOffice doit etre installe sur le serveur (non embarque dans le JAR)
- `log4j-core` est exclu de POI et redirige via `log4j-to-slf4j` pour eviter les conflits avec Logback
- macOS : message `Task policy set failed` au demarrage de LibreOffice (inoffensif, voir commentaire dans `PdfConversionService.kt`)

## Configuration

Fichier `src/main/resources/application.yaml` ou variables d'environnement :

| Variable | Defaut | Description |
|----------|--------|-------------|
| `PORT` | 8080 | Port HTTP |
| `TEMPLATES_DIR` | `templates` | Repertoire des templates DOCX |
| `OUTPUT_DIR` | `output` | Repertoire de sortie PDF |
| `SAVE_TO_DISC` | `false` | Sauvegarder les PDF sur disque |
| `app.libreoffice.port` | 2002 | Port de base du pool LibreOffice |
| `app.libreoffice.poolSize` | 2 | Nombre d'instances LibreOffice |
