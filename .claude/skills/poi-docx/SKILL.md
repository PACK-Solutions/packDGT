---
name: poi-docx
description: Apache POI XWPF and OpenSagres - use when working with DOCX templates, placeholder replacement, or DOCX-to-PDF conversion
---

# Apache POI 5.x + OpenSagres — Guide packDGT

## Versions

- Apache POI **5.3.0** (poi-ooxml)
- OpenSagres xdocreport **2.0.4** (fr.opensagres.poi.xwpf.converter.pdf)

## Architecture

Deux services distincts :
1. **`TemplateService`** (POI) : lit le DOCX template, remplace les `{{placeholders}}`, produit un DOCX modifié
2. **`PdfConversionService`** (OpenSagres) : convertit le DOCX en PDF via iText 2.1.7

## Système de placeholders

Format : `{{nomDuChamp}}` — camelCase, entouré de doubles accolades.

### Problème connu : runs fragmentés

Word peut découper `{{customerName}}` en plusieurs runs XML :
- Run 1 : `{{`
- Run 2 : `customerName`
- Run 3 : `}}`

**Solution implémentée** dans `TemplateService.replaceInParagraph()` :
1. Concaténer tous les textes des runs du paragraphe
2. Effectuer les remplacements sur le texte complet
3. Réécrire le résultat dans le premier run, vider les autres

```kotlin
val fullText = runs.joinToString("") { runText(it) ?: "" }
var replacedText = fullText
for ((key, value) in data) {
    replacedText = replacedText.replace("{{$key}}", value)
}
runs.first().setText(replacedText, 0)
for (i in 1 until runs.size) { runs[i].setText("", 0) }
```

### Zones de remplacement

Le moteur cherche les placeholders dans :
- Paragraphes du corps
- Cellules de tableaux
- En-têtes (`headerList`)
- Pieds de page (`footerList`)

## Créer un template DOCX compatible

Voir `tools/TemplateGenerator.kt`. Contraintes **obligatoires** pour OpenSagres :

```kotlin
val doc = XWPFDocument()

// 1. Styles — OBLIGATOIRE
doc.createStyles()

// 2. Section properties — OBLIGATOIRE (sinon NPE dans MasterPageManager)
val sectPr = doc.document.body.addNewSectPr()
val pgSz = sectPr.addNewPgSz()
pgSz.w = BigInteger.valueOf(11906)  // A4 largeur twips
pgSz.h = BigInteger.valueOf(16838)  // A4 hauteur twips
pgSz.orient = STPageOrientation.PORTRAIT
val pgMar = sectPr.addNewPgMar()
pgMar.top = BigInteger.valueOf(1440)
pgMar.bottom = BigInteger.valueOf(1440)
pgMar.left = BigInteger.valueOf(1440)
pgMar.right = BigInteger.valueOf(1440)

// 3. Grille de tableau — OBLIGATOIRE (sinon NPE dans XWPFTableUtil)
val table = doc.createTable(rows, cols)
val grid = table.ctTbl.let { if (it.tblGrid == null) it.addNewTblGrid() else it.tblGrid }
if (grid.gridColList.isEmpty()) {
    grid.addNewGridCol().w = BigInteger.valueOf(4500)
    grid.addNewGridCol().w = BigInteger.valueOf(4500)
}
```

## Compatibilité POI 5.x / OpenSagres 2.0.4

OpenSagres 2.0.4 a été conçu pour POI 4.x. Avec POI 5.x, certaines structures internes doivent être explicites :
- `styles.xml` doit exister (→ `createStyles()`)
- `sectPr` (section properties) doit exister dans le body
- Les grilles de tableaux (`tblGrid`) doivent avoir des colonnes explicites

Les templates créés avec Microsoft Word ou LibreOffice n'ont généralement **pas** ces problèmes car ces outils génèrent toujours les structures complètes.

## Sécurité

- **Path traversal** : `TemplateService.resolveTemplatePath()` utilise `Path.of(name).fileName` pour nettoyer le chemin
- Les templates sont limités au répertoire configuré (`templates/`)

## Log4j

POI dépend de `log4j-core`. On l'exclut dans `build.gradle.kts` et on redirige via `log4j-to-slf4j` pour unifier sur Logback.
