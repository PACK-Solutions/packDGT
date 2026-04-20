# packDGT - Document Generation Tool

API REST de génération de PDF à partir de templates Word/DOCX et de données JSON.

## Stack technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| Langage | Kotlin | 2.1.0 |
| Framework HTTP | Ktor | 3.0.3 |
| Build | Gradle (Kotlin DSL) | 8.12 |
| JVM | Java | 21 |
| DOCX manipulation | Apache POI (XWPF) | 5.3.0 |
| DOCX → PDF | OpenSagres xdocreport | 2.0.4 |
| PDF post-processing | Apache PDFBox | 3.0.3 |
| JSON | Jackson | 2.18.1 |
| Logging | Logback + SLF4J | 1.5.11 |
| Tests | JUnit 5 + Ktor Test Host | 5.11.3 |

## Architecture

```
src/main/kotlin/com/example/packdgt/
├── Application.kt              # Point d'entrée, module Ktor
├── config/AppConfig.kt         # Configuration YAML → data class
├── exception/AppExceptions.kt  # Hiérarchie sealed d'exceptions métier
├── api/
│   ├── dto/Dto.kt              # GenerateRequest, GenerateOptions, ErrorResponse, HealthResponse
│   ├── plugins/Plugins.kt      # ContentNegotiation, CallId, CallLogging, StatusPages
│   └── routes/DocumentRoutes.kt # GET /health, GET /templates, POST /generate
├── service/
│   ├── DocumentGenerationService.kt  # Orchestrateur du pipeline
│   ├── TemplateService.kt            # POI : remplacement placeholders {{key}}
│   ├── PdfConversionService.kt       # OpenSagres : DOCX → PDF
│   └── PdfPostProcessingService.kt   # PDFBox : watermark, métadonnées, pagination, protection
└── tools/TemplateGenerator.kt  # Utilitaire création template DOCX d'exemple
```

### Pipeline de génération

```
JSON request → Validation → POI (placeholder replacement) → OpenSagres (DOCX→PDF) → PDFBox (post-processing) → HTTP response (binary PDF)
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
- `POST /generate` — génère un PDF, retourne le binaire (Content-Type: application/pdf)

## Conventions

- Placeholders DOCX simples : `{{nomDuChamp}}` (camelCase)
- Tableaux dynamiques : marqueur `{{#tableName}}` dans la 1re cellule de la ligne-modèle
- Le champ `tables` du JSON contient les données tabulaires : `Map<String, List<List<String>>>`
- Templates stockés dans `templates/` (configurable via `TEMPLATES_DIR`)
- Fichiers `.http` IntelliJ dans `http/` pour tester l'API
- Gestion d'erreurs via `sealed class AppException` → StatusPages mapping HTTP
- Correlation ID automatique dans les logs (header `X-Request-Id` ou auto-généré)
- DI manuelle (pas de framework), services instanciés dans `Application.module()`

## Contraintes connues

- OpenSagres 2.0.4 requiert que les DOCX aient : styles (`createStyles()`), section properties (`sectPr`), et grille de tableau explicite. Voir `TemplateGenerator.kt` pour l'exemple.
- La conversion DOCX→PDF utilise iText 2.1.7 (LGPL) via OpenSagres — rendu basique, pas de support SmartArt/images complexes.
- `log4j-core` est exclu de POI et redirigé via `log4j-to-slf4j` pour éviter les conflits avec Logback.

## Configuration

Fichier `src/main/resources/application.yaml` ou variables d'environnement :

| Variable | Défaut | Description |
|----------|--------|-------------|
| `PORT` | 8080 | Port HTTP |
| `TEMPLATES_DIR` | `templates` | Répertoire des templates DOCX |
| `OUTPUT_DIR` | `output` | Répertoire de sortie PDF |
| `SAVE_TO_DISC` | `false` | Sauvegarder les PDF sur disque |
