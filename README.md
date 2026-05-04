# packDGT — Document Generation Tool

API REST de generation de PDF a partir de templates Word/DOCX et de donnees JSON.

## Fonctionnalites

- Remplacement de **placeholders** `{{key}}` dans un template DOCX
- **Tableaux dynamiques** : generation de lignes a partir de donnees JSON (ex: mouvements, sinistres, garanties)
- Conversion DOCX vers PDF fidele via **LibreOffice** (pool JODConverter)
- **Post-traitement PDF** via PDFBox :
  - Watermark en filigrane (texte, rotation 45deg, transparence)
  - Metadonnees (auteur, titre, sujet)
  - Pagination (numerotation en pied de page)
  - Protection (lecture seule, impression autorisee)
- Reponse HTTP binaire (PDF) ou sauvegarde sur disque

## Stack technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| Langage | Kotlin | 2.1.0 |
| Framework HTTP | Ktor | 3.0.3 |
| Build | Gradle (Kotlin DSL) | 8.12 |
| JVM | Java | 21 |
| DOCX manipulation | Apache POI (XWPF) | 5.3.0 |
| DOCX vers PDF | JODConverter + LibreOffice | 4.4.7 |
| PDF post-traitement | Apache PDFBox | 3.0.3 |
| JSON | Jackson | 2.18.1 |
| Tests | JUnit 5 + Ktor Test Host | 5.11.3 |

## Pre-requis

- **Java 21+**
- **LibreOffice** installe et `soffice` dans le PATH

```bash
# macOS
brew install --cask libreoffice

# Linux (Debian/Ubuntu)
sudo apt install libreoffice-core libreoffice-writer

# Verification
soffice --version
```

> **Note macOS :** le message `Task policy set failed: 4 ((os/kern) invalid argument)` apparait au demarrage de LibreOffice. C'est un avertissement inoffensif du noyau macOS qui n'affecte pas le fonctionnement.

## Demarrage rapide

```bash
# Build + tests
./gradlew build

# Lancer le serveur
./gradlew run
# -> API   : http://localhost:8080
# -> UI    : http://localhost:8080/ui
```

Au demarrage, l'application lance un pool de **2 instances LibreOffice** residentes (ports 2002-2003). Le premier appel prend ~1.8s (bootstrap UNO), les suivants ~70ms.

L'interface web (UI) est servie sur `/ui` et permet de generer, previsualiser, completer (texte libre) et telecharger les PDF interactivement.

Le template d'exemple `attestation-assurance.docx` est genere automatiquement dans `templates/` lors du premier lancement des tests.

## Performance

Le pipeline utilise JODConverter pour maintenir un pool d'instances LibreOffice en memoire, evitant le cold-start de ~1.7s par requete.

| Metrique | Temps |
|----------|-------|
| 1re requete (warm-up UNO) | ~1.8s |
| Requetes suivantes | **~70ms** |
| 3 requetes paralleles | **~140ms** les 3 |

Decomposition serveur (regime permanent) :

| Etape | Temps |
|-------|-------|
| Template (POI + cache memoire) | ~10ms |
| Conversion (LibreOffice via socket UNO) | ~57ms |
| Post-traitement (PDFBox) | ~3ms |

## API

### `GET /health`

```json
{ "status": "UP", "version": "1.0.0", "templatesCount": 1 }
```

### `GET /templates`

```json
{ "templates": ["attestation-assurance.docx"] }
```

### `POST /generate`

Genere un PDF et le retourne en reponse binaire.

**Requete :**

```json
{
  "templateName": "attestation-assurance.docx",
  "outputFileName": "attestation-123.pdf",
  "data": {
    "customerName": "Jean Dupont",
    "policyNumber": "POL-2026-000123",
    "startDate": "2026-04-01",
    "endDate": "2027-03-31",
    "premium": "1250,50 EUR",
    "address": "10 rue Exemple, 75001 Paris"
  },
  "tables": {
    "movements": [
      ["01/04/2026", "Prelevement prime mensuelle", "-125,05 EUR", "8 432,50 EUR"],
      ["15/03/2026", "Remboursement sinistre #S-2026-042", "+2 500,00 EUR", "8 557,55 EUR"],
      ["01/03/2026", "Prelevement prime mensuelle", "-125,05 EUR", "6 057,55 EUR"]
    ]
  },
  "options": {
    "watermark": "CONFIDENTIEL",
    "author": "Insurance API",
    "title": "Attestation d'assurance",
    "protect": false,
    "saveToDisc": false
  }
}
```

**Reponse :** `200 OK` avec `Content-Type: application/pdf` et header `Content-Disposition`.

### Champs de la requete

| Champ | Type | Requis | Description |
|-------|------|--------|-------------|
| `templateName` | string | oui | Nom du fichier template (doit finir par `.docx`) |
| `outputFileName` | string | non | Nom du PDF de sortie (defaut : nom du template + `.pdf`) |
| `data` | object | non | Paires cle/valeur pour les placeholders `{{key}}` |
| `tables` | object | non | Tableaux dynamiques (cle = nom du marqueur, valeur = lignes) |
| `options.watermark` | string | non | Texte du filigrane |
| `options.author` | string | non | Auteur dans les metadonnees PDF |
| `options.title` | string | non | Titre dans les metadonnees PDF |
| `options.subject` | string | non | Sujet dans les metadonnees PDF |
| `options.protect` | boolean | non | Protection lecture seule (defaut : false) |
| `options.saveToDisc` | boolean | non | Sauvegarder le PDF dans `OUTPUT_DIR` |

### Codes d'erreur

| Code | Situation |
|------|-----------|
| 400 | `templateName` vide ou extension invalide |
| 404 | Template DOCX introuvable |
| 422 | Erreur de traitement du template |
| 500 | Erreur de conversion ou post-traitement PDF |

Chaque erreur retourne un JSON avec `correlationId` pour le tracage :

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Template introuvable : inexistant.docx",
  "correlationId": "a1b2c3d4"
}
```

## Systeme de templates

### Placeholders simples

Dans le template DOCX, les placeholders suivent le format `{{camelCase}}` :

```
Nom de l'assure : {{customerName}}
Numero de police : {{policyNumber}}
```

Les placeholders sont remplaces dans :
- Les paragraphes du corps
- Les cellules de tableaux
- Les en-tetes et pieds de page

### Tableaux dynamiques

Un tableau dynamique est identifie par un **marqueur** `{{#tableName}}` dans la premiere cellule d'une ligne-modele :

```
| Date | Libelle        | Montant    | Solde      |   <-- en-tete (conserve)
| {{#movements}}                                   |   <-- ligne-modele (supprimee et remplacee)
```

Les donnees correspondantes sont fournies dans le champ `tables` du JSON :

```json
{
  "tables": {
    "movements": [
      ["01/04/2026", "Prelevement prime", "-125,05 EUR", "8 432,50 EUR"],
      ["15/03/2026", "Remboursement sinistre", "+2 500,00 EUR", "8 557,55 EUR"]
    ]
  }
}
```

Chaque sous-liste correspond a une ligne, chaque element a une cellule (dans l'ordre des colonnes).

## Tests avec IntelliJ IDEA

Le dossier `http/` contient des fichiers `.http` utilisables directement dans IntelliJ IDEA (ou tout client HTTP compatible) :

| Fichier | Description |
|---------|-------------|
| `health.http` | Health check et liste des templates |
| `generate-simple.http` | Generation basique sans options |
| `generate-full.http` | Generation complete avec tableaux dynamiques et toutes les options |
| `generate-protected.http` | Generation avec protection PDF |
| `generate-save-to-disc.http` | Generation avec sauvegarde sur disque |
| `errors.http` | Cas d'erreur (404, 400, correlation ID) |

Pour les utiliser :
1. Lancer le serveur : `./gradlew run`
2. Ouvrir un fichier `.http` dans IntelliJ
3. Cliquer sur le bouton **Run** a cote de chaque requete

## Configuration

| Variable d'environnement | Defaut | Description |
|--------------------------|--------|-------------|
| `PORT` | `8080` | Port HTTP |
| `TEMPLATES_DIR` | `templates` | Repertoire des templates DOCX |
| `OUTPUT_DIR` | `output` | Repertoire de sortie PDF |
| `SAVE_TO_DISC` | `false` | Sauvegarde systematique des PDF |

Configuration avancee dans `application.yaml` :

| Propriete | Defaut | Description |
|-----------|--------|-------------|
| `app.libreoffice.port` | `2002` | Port de base pour le pool LibreOffice |
| `app.libreoffice.poolSize` | `2` | Nombre d'instances LibreOffice dans le pool |

## Architecture

```
src/main/kotlin/com/example/packdgt/
├── Application.kt                        # Point d'entree, module Ktor, cycle de vie LO
├── config/AppConfig.kt                   # Configuration YAML -> data class
├── exception/AppExceptions.kt            # Hierarchie sealed d'exceptions
├── api/
│   ├── dto/Dto.kt                        # DTOs requete/reponse
│   ├── plugins/Plugins.kt               # Plugins Ktor
│   └── routes/DocumentRoutes.kt          # Routes REST
├── service/
│   ├── DocumentGenerationService.kt      # Orchestrateur du pipeline (avec metriques)
│   ├── TemplateService.kt                # POI : placeholders + tableaux + cache memoire
│   ├── PdfConversionService.kt           # JODConverter : pool LibreOffice resident
│   └── PdfPostProcessingService.kt       # PDFBox : watermark, metadata, protection
└── tools/TemplateGenerator.kt            # Utilitaire creation template
```

### Pipeline de generation

```
JSON request
    -> Validation
    -> Apache POI (remplacement placeholders + expansion tableaux)  [~10ms]
    -> JODConverter/LibreOffice (conversion DOCX -> PDF via UNO)    [~57ms]
    -> PDFBox (watermark + metadonnees + pagination + protection)   [~3ms]
    -> HTTP response (binary PDF)                                   [~70ms total]
```

### Optimisations

- **Pool LibreOffice resident** : JODConverter maintient des instances soffice en memoire et communique par socket UNO, eliminant le cold-start de ~1.7s par requete
- **Cache template en memoire** : `ConcurrentHashMap` avec invalidation sur `lastModified`, evite les lectures disque repetees
- **Fonts PDFBox pre-instanciees** : les objets `PDType1Font` sont crees une fois et reutilises (thread-safe)
- **Constantes trigonometriques pre-calculees** : cos/sin du watermark calcules au demarrage
- **Metriques de timing** : chaque etape du pipeline est chronometree dans les logs

## Tests

```bash
./gradlew test                    # Tous les tests (31)
./gradlew test --tests "*.TemplateServiceTest"  # Tests template uniquement
```

| Suite | Tests | Couverture |
|-------|-------|------------|
| `TemplateServiceTest` | 10 | Placeholders, tableaux statiques/dynamiques, tableau vide, securite |
| `PdfPostProcessingServiceTest` | 6 | Metadonnees, watermark, protection, pagination |
| `PdfConversionServiceTest` | 2 | Conversion DOCX valide, tableaux dynamiques |
| `DocumentGenerationServiceTest` | 6 | Pipeline complet avec tableaux, sauvegarde, validation |
| `DocumentRoutesTest` | 7 | Endpoints HTTP, codes d'erreur, PDF avec tableaux |

## Limites connues

- **Pre-requis systeme** : LibreOffice doit etre installe sur le serveur (non embarque dans le JAR)
- **Placeholders fragmentes** : Word peut decouper `{{key}}` sur plusieurs runs XML. Le moteur gere ce cas en concatenant les runs, mais des enrichissements de style au milieu d'un placeholder peuvent poser probleme
- **Tableaux dynamiques** : un seul marqueur `{{#name}}` par tableau. Pas de tableaux imbriques
- **macOS** : le message `Task policy set failed` apparait au demarrage de LibreOffice (inoffensif)

## Pistes d'evolution

- **Templating avance** : boucles, conditions, images dynamiques (via docx-stamper)
- **Stockage S3/MinIO** pour les templates et PDF generes
- **File d'attente** (RabbitMQ/Kafka) pour la generation en masse
- **Signature PDF** numerique (PAdES)
- **PDF/A-3** pour l'archivage reglementaire
- **Metriques** Micrometer/Prometheus
- **Docker** : image avec LibreOffice pre-installe pour deploiement simplifie
