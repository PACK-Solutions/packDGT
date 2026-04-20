---
name: pdfbox
description: Apache PDFBox 3.x operations - use when adding PDF post-processing features like watermarks, metadata, protection, merging, or page manipulation
---

# Apache PDFBox 3.x — Guide packDGT

## Version

PDFBox **3.0.3** (migration depuis 2.0.x effectuée).

## Rôle dans packDGT

`PdfPostProcessingService.kt` utilise PDFBox pour le post-traitement du PDF **après** la conversion DOCX→PDF par OpenSagres. Il ne sert **pas** à la conversion DOCX→PDF (impossible avec PDFBox seul).

## API PDFBox 3 — Changements depuis 2.0

| PDFBox 2.0 | PDFBox 3.0 |
|------------|------------|
| `PDDocument.load(bytes)` | `Loader.loadPDF(bytes)` |
| `PDType1Font.HELVETICA_BOLD` | `PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)` |
| `PDType1Font.HELVETICA` | `PDType1Font(Standard14Fonts.FontName.HELVETICA)` |
| `setNonStrokingColor(r, g, b)` (int) | `setNonStrokingColor(Color(r, g, b))` (java.awt.Color) |

## Imports clés (PDFBox 3)

```kotlin
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
```

## Patterns implémentés

### Charger un PDF

```kotlin
Loader.loadPDF(pdfBytes).use { document ->
    // manipulations...
    ByteArrayOutputStream().use { output ->
        document.save(output)
        return output.toByteArray()
    }
}
```

### Watermark en filigrane

```kotlin
val font = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
    val gs = PDExtendedGraphicsState()
    gs.nonStrokingAlphaConstant = 0.15f  // Transparence
    cs.setGraphicsStateParameters(gs)
    cs.setFont(font, 52f)
    cs.setNonStrokingColor(Color(150, 150, 150))
    cs.beginText()
    cs.setTextMatrix(Matrix(cos, sin, -sin, cos, offsetX, offsetY))  // Rotation 45°
    cs.showText(text)
    cs.endText()
}
```

### Métadonnées

```kotlin
val info = document.documentInformation
info.author = "Mon Application"
info.title = "Titre du document"
info.creator = "packDGT"
info.creationDate = Calendar.getInstance()
```

### Protection (chiffrement)

```kotlin
val permissions = AccessPermission()
permissions.setCanPrint(true)
permissions.setCanModify(false)
val policy = StandardProtectionPolicy("", "", permissions)
policy.encryptionKeyLength = 128
document.protect(policy)
```

### Pagination (numéros de page en footer)

```kotlin
val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
    cs.setFont(font, 9f)
    cs.beginText()
    cs.newLineAtOffset(x, 25f)
    cs.showText("Page ${index + 1} / $totalPages")
    cs.endText()
}
```

## Fonctionnalités PDFBox utilisables en extension

- **Fusion de PDF** : `PDFMergerUtility`
- **Extraction de texte** : `PDFTextStripper`
- **Ajout de pages vierges** : `document.addPage(PDPage(PDRectangle.A4))`
- **Signature numérique** : `PDFBoxSigner` (complexe, nécessite un certificat)
- **Conversion PDF/A** : possible via les métadonnées XMP et l'embarquement de polices

## Tests

Les tests PDFBox créent des PDF minimaux avec `PDDocument()` + `addPage()`, puis vérifient les transformations via `Loader.loadPDF()`.
