package com.example.packdgt.config

import com.example.packdgt.api.dto.FieldDef
import com.example.packdgt.api.dto.TableDef
import com.example.packdgt.api.dto.TemplateMetadata

object TemplateRegistry {

    private val templates = mapOf(
        "attestation-assurance.docx" to TemplateMetadata(
            name = "attestation-assurance.docx",
            label = "Attestation d'assurance",
            fields = listOf(
                FieldDef("customerName", "Nom du client", "Jean Dupont"),
                FieldDef("policyNumber", "Numéro de police", "POL-2026-000123"),
                FieldDef("startDate", "Date de début", "2026-04-01"),
                FieldDef("endDate", "Date de fin", "2027-03-31"),
                FieldDef("premium", "Prime", "1250,50 €"),
                FieldDef("address", "Adresse", "10 rue Exemple, 75001 Paris")
            ),
            tables = listOf(
                TableDef("movements", "Mouvements", listOf("Date", "Libellé", "Montant", "Solde"))
            )
        ),
        "releve-compte-multi.docx" to TemplateMetadata(
            name = "releve-compte-multi.docx",
            label = "Relevé de compte annuel",
            fields = listOf(
                // Identification
                FieldDef("numeroAdherent", "N° adhérent", "7161694", "Identification"),
                FieldDef("identifiantClient", "Identifiant client", "905168641", "Identification"),
                FieldDef("numeroContrat", "N° contrat", "AVA0001111", "Identification"),
                FieldDef("civilite", "Civilité", "M.", "Identification"),
                FieldDef("prenom", "Prénom", "Arnaud", "Identification"),
                FieldDef("nom", "Nom", "Le Saint", "Identification"),
                FieldDef("adresseLigne1", "Adresse ligne 1", "LIEU DIT LES ROBERTS", "Identification"),
                FieldDef("adresseLigne2", "Adresse ligne 2", "05310 FREISSINIERES", "Identification"),

                // Dates
                FieldDef("dateReleve", "Date du relevé", "31 DÉCEMBRE 2025", "Dates"),
                FieldDef("dateReleveCourt", "Date du relevé (court)", "31/12/2025", "Dates"),
                FieldDef("dateEffetContrat", "Date d'effet du contrat", "19/01/2017", "Dates"),
                FieldDef("cadreFiscal", "Cadre fiscal", "Assurance-vie", "Dates"),
                FieldDef("dateTermeContrat", "Date terme du contrat", "19/01/2026", "Dates"),
                FieldDef("datePrecedente", "Date précédente", "31/12/2024", "Dates"),
                FieldDef("datePrecedente3ans", "Date précédente 3 ans", "31/12/2022", "Dates"),
                FieldDef("datePrecedente5ans", "Date précédente 5 ans", "31/12/2020", "Dates"),
                FieldDef("dateCourante", "Date courante", "31/12/2025", "Dates"),
                FieldDef("anneeCourante", "Année courante", "2025", "Dates"),
                FieldDef("anneeProchaine", "Année prochaine", "2026", "Dates"),

                // Épargne
                FieldDef("epargnePrecedente", "Épargne précédente", "56 229.87 €", "Épargne"),
                FieldDef("totalVersementsNets", "Total versements nets", "0.00 €", "Épargne"),
                FieldDef("totalRachats", "Total rachats", "56 000.00 €", "Épargne"),
                FieldDef("totalArbitragesInvestis", "Total arbitrages investis", "0.00 €", "Épargne"),
                FieldDef("totalArbitragesDesinvestis", "Total arbitrages désinvestis", "0.00 €", "Épargne"),
                FieldDef("interetsTechniquesInvestis", "Intérêts techniques investis", "300.45 €", "Épargne"),
                FieldDef("cotisationPlancherEuros", "Cotisation plancher euros", "0.00 €", "Épargne"),
                FieldDef("plusMoinsValueUCInvestie", "Plus/moins-value UC investie", "0.00 €", "Épargne"),
                FieldDef("plusMoinsValueUCDesinvestie", "Plus/moins-value UC désinvestie", "0.00 €", "Épargne"),
                FieldDef("cotisationPlancherUC", "Cotisation plancher UC", "0.00 €", "Épargne"),
                FieldDef("epargneCourante", "Épargne courante", "889.28 €", "Épargne"),

                // Rachats
                FieldDef("totalRachatsPartiels", "Total rachats partiels", "56 000.00 €", "Rachats"),

                // Valeur de rachat
                FieldDef("valeurRachat", "Valeur de rachat", "889.28 €", "Valeur de rachat"),
                FieldDef("montantSupportEuros", "Montant support euros", "775.75 €", "Valeur de rachat"),
                FieldDef("fraisSupportEuros", "Frais support euros", "-131.48", "Valeur de rachat"),

                // Rendement
                FieldDef("tauxMoyenRendement", "Taux moyen de rendement", "3.75 %", "Rendement"),
                FieldDef("tauxRendementMinGaranti", "Taux rendement min garanti", "2.00 %", "Rendement"),
                FieldDef("montantGarantiEuros", "Montant garanti euros", "776.51 €", "Rendement"),
                FieldDef("dateGarantie", "Date de garantie", "19/01/2026", "Rendement"),

                // Versements cumulés
                FieldDef("cumulVersementsBrutsPrecedent", "Cumul versements bruts précédent", "50 500.00 €", "Versements cumulés"),
                FieldDef("cumulVersementsBrutsCourant", "Cumul versements bruts courant", "522.55 €", "Versements cumulés"),

                // Frais
                FieldDef("fraisEpargneGereeTaux", "Taux frais épargne gérée", "0.70 %", "Frais"),
                FieldDef("fraisEpargneGereeMontant", "Montant frais épargne gérée", "131.48 €", "Frais"),
                FieldDef("fraisEpargneGereeUCMontant", "Montant frais épargne gérée UC", "32.37 €", "Frais"),
                FieldDef("fraisFinanciersUCMontant", "Montant frais financiers UC", "32.37 €", "Frais"),
                FieldDef("fraisArbitragesTaux", "Taux frais arbitrages", "0.00 %", "Frais"),
                FieldDef("fraisArbitragesMontant", "Montant frais arbitrages", "0.00 €", "Frais"),
                FieldDef("totalFraisPrelevesAnnee", "Total frais prélevés année", "196.22 €", "Frais"),

                // Intérêts et participations
                FieldDef("totalInteretsTechniquesInvestis", "Total intérêts techniques investis", "494.34 €", "Intérêts et participations"),
                FieldDef("totalInteretsTechniquesDesinvestis", "Total intérêts techniques désinvestis", "193.89 €", "Intérêts et participations"),
                FieldDef("tauxRendementMinGarantiEuros", "Taux rendement min garanti euros", "0.00 %", "Intérêts et participations"),
                FieldDef("montantRendementMinGaranti", "Montant rendement min garanti", "0.00 €", "Intérêts et participations"),
                FieldDef("tauxParticipationExcedents", "Taux participation excédents", "0.00 %", "Intérêts et participations"),
                FieldDef("montantParticipationExcedents", "Montant participation excédents", "494.34 €", "Intérêts et participations"),
                FieldDef("tauxFraisEpargneGeree", "Taux frais épargne gérée", "0.70 %", "Intérêts et participations"),
                FieldDef("montantFraisEpargneGeree", "Montant frais épargne gérée", "131.48 €", "Intérêts et participations"),
                FieldDef("tauxPrelevementsSociaux", "Taux prélèvements sociaux", "17.20 %", "Intérêts et participations"),
                FieldDef("montantPrelevementsSociaux", "Montant prélèvements sociaux", "62.41 €", "Intérêts et participations"),
                FieldDef("montantCotisationPlancher", "Montant cotisation plancher", "0.00 €", "Intérêts et participations")
            ),
            tables = listOf(
                TableDef("rachats", "Rachats", listOf("Nature", "Date demande", "Date effet", "", "", "Montant")),
                TableDef("situationSupportsUC", "Situation supports UC", listOf("Support", "Valeur", "Nb parts", "Montant", "Frais")),
                TableDef("evolutionUC", "Évolution UC", listOf("Support", "Perf. origine", "Perf. 1 an", "Perf. 3 ans", "Perf. 5 ans")),
                TableDef("evolutionSupportEuros", "Évolution support euros", listOf("Nature", "Date début", "Date fin", "Taux", "", "Crédit", "Débit")),
                TableDef("nbUCGaranti", "Nombre UC garanti", listOf("Support", "Nb parts", "Taux")),
                TableDef("syntheseFrais", "Synthèse des frais", listOf("Nature", "Taux", "Montant")),
                TableDef("detailFraisUC", "Détail frais UC", listOf("Support", "Taux gestion", "Montant gestion", "Taux financiers", "Montant financiers", "Total")),
                TableDef("actifsFinanciers", "Actifs financiers", listOf("ISIN", "Support", "SRI", "Perf. origine", "", "Frais courants", "Perf. 1 an", "", "Frais récurrents", "Frais ponctuels", "Perf. 3 ans", ""))
            )
        )
    )

    fun getAll(): List<TemplateMetadata> = templates.values.toList()

    fun get(name: String): TemplateMetadata? = templates[name]
}
