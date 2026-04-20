---
name: run-and-test
description: Build, test, and run the packDGT application - use when asked to run tests, start the server, or verify the build
disable-model-invocation: true
allowed-tools: Bash
---

# Build, Test & Run — packDGT

## Commandes principales

```bash
# Build complet (compile + tests)
./gradlew build

# Tests uniquement
./gradlew test

# Test spécifique
./gradlew test --tests "com.example.packdgt.service.TemplateServiceTest"

# Lancer le serveur
./gradlew run
# Accessible sur http://localhost:8080

# Clean build
./gradlew clean build
```

## Vérification rapide après modifications

1. `./gradlew compileKotlin` — vérifier la compilation
2. `./gradlew test` — lancer les 28 tests
3. Si tout passe, `./gradlew run` et tester manuellement

## Test end-to-end manuel

```bash
# Health check
curl http://localhost:8080/health

# Lister les templates
curl http://localhost:8080/templates

# Générer un PDF
curl -o test.pdf -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "attestation-assurance.docx",
    "outputFileName": "test.pdf",
    "data": {
      "customerName": "Jean Dupont",
      "policyNumber": "POL-2026-000123",
      "startDate": "2026-04-01",
      "endDate": "2027-03-31",
      "premium": "1250,50 €",
      "address": "10 rue Exemple, 75001 Paris"
    },
    "options": {
      "watermark": "CONFIDENTIEL",
      "author": "Insurance API",
      "title": "Attestation"
    }
  }'

# Vérifier le PDF
file test.pdf  # doit afficher "PDF document"
```

## Suites de tests

| Suite | Fichier | Tests |
|-------|---------|-------|
| Template POI | `service/TemplateServiceTest.kt` | 6 |
| Conversion PDF | `service/PdfConversionServiceTest.kt` | 2 |
| Post-traitement PDFBox | `service/PdfPostProcessingServiceTest.kt` | 6 |
| Orchestration | `service/DocumentGenerationServiceTest.kt` | 6 |
| Routes API | `api/DocumentRoutesTest.kt` | 6 |

## Dépendances

Si Gradle ne résout pas les dépendances :
- POI exclut `log4j-core` → vérifier que `log4j-to-slf4j` est présent
- OpenSagres tire iText 2.1.7 (LGPL) — pas de conflit avec PDFBox
- JVM toolchain 21 requis
