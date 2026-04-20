---
name: generate-template
description: Create or modify DOCX templates for document generation - use when the user wants to add a new template, modify placeholder structure, or debug template issues
disable-model-invocation: true
argument-hint: [template-name]
---

# Créer / modifier un template DOCX

## Créer un nouveau template

Pour créer un template `$ARGUMENTS.docx`, suivre le pattern de `tools/TemplateGenerator.kt` :

1. Créer une fonction dans `tools/TemplateGenerator.kt` ou un nouveau fichier dans `tools/`
2. Respecter les contraintes OpenSagres obligatoires :
   - `doc.createStyles()`
   - Ajouter `sectPr` avec dimensions A4
   - Grilles de tableaux explicites
3. Utiliser des placeholders `{{camelCase}}`
4. Sauvegarder dans `templates/`

## Checklist template

- [ ] `createStyles()` appelé
- [ ] Section properties (A4, marges) définie
- [ ] Grilles de tableaux avec `addNewGridCol()` pour chaque colonne
- [ ] Placeholders au format `{{key}}` en camelCase
- [ ] Tester la conversion PDF : les styles complexes (images, SmartArt) ne seront pas rendus

## Template depuis Word/LibreOffice

Les templates créés avec un éditeur Word n'ont pas besoin des ajustements OpenSagres — les structures internes sont complètes. Il suffit de :
1. Créer le document avec les placeholders `{{key}}` en texte brut
2. Sauvegarder en `.docx`
3. Copier dans `templates/`
4. Tester avec `POST /generate`

## Tester un template

```bash
curl -o test.pdf -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"templateName":"$ARGUMENTS.docx","data":{"key1":"val1","key2":"val2"}}'
```
