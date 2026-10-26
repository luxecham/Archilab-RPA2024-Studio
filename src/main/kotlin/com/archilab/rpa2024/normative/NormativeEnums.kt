package com.archilab.rpa2024.normative

/** Erreur de resolution des tables RPA 2024 / CBA 93. */
class NormativeDataException(message: String) : IllegalArgumentException(message)

enum class SpectrumType {
    /** Zones IV, V et VI - Tableau 3.3 du RPA 2024. */
    TYPE_1,

    /** Zones I, II et III - Tableau 3.4 du RPA 2024. */
    TYPE_2
}

/**
 * Tableau 3.2 du RPA 2024 : coefficient d'acceleration de reference de zone A, au rocher (S1),
 * pour Tr = 475 ans. La zone 0 ne comporte pas d'action sismique de calcul.
 */
enum class SeismicZone(val code: String, val level: String, val coeffA: Double) {
    ZONE_0("0", "Très faible", 0.0),
    ZONE_I("I", "Faible", 0.07),
    ZONE_II("II", "Faible à moyenne", 0.10),
    ZONE_III("III", "Moyenne", 0.15),
    ZONE_IV("IV", "Moyenne à élevée", 0.20),
    ZONE_V("V", "Élevée", 0.25),
    ZONE_VI("VI", "Élevée", 0.30);

    val hasSeismicAction: Boolean get() = coeffA > 0.0

    val spectrumType: SpectrumType
        get() = when (this) {
            ZONE_IV, ZONE_V, ZONE_VI -> SpectrumType.TYPE_1
            else -> SpectrumType.TYPE_2
        }

    companion object {
        fun fromCode(code: String): SeismicZone {
            val normalized = code.trim().uppercase().removePrefix("ZONE_").removePrefix("ZONE")
            return entries.firstOrNull { it.code.equals(normalized, ignoreCase = true) || it.name == "ZONE_$normalized" }
                ?: throw NormativeDataException(
                    "Zone sismique '$code' inconnue. Valeurs admises : 0, I, II, III, IV, V, VI (Tableau 3.2)."
                )
        }
    }
}

/**
 * Tableau 3.1 du RPA 2024. S1 a S4 sont les quatre classes tabulees, SS designe un site
 * necessitant des investigations approfondies et une etude specifique (aucun spectre tabule).
 */
enum class SiteClass(val code: String, val description: String) {
    S1("S1", "Rocheux"),
    S2("S2", "Ferme"),
    S3("S3", "Meuble"),
    S4("S4", "Très meuble"),
    SS("SS", "Investigations spécifiques");

    val hasTabulatedSpectrum: Boolean get() = this != SS

    companion object {
        fun fromCode(code: String): SiteClass {
            val normalized = code.trim().uppercase()
            return entries.firstOrNull { it.code == normalized || it.name == normalized }
                ?: throw NormativeDataException(
                    "Classe de site '$code' inconnue. Valeurs admises : S1, S2, S3, S4, SS (Tableau 3.1)."
                )
        }
    }
}

/** Tableau 3.10 et annexe I : I, Tr et coefficient reducteur nu_A. */
enum class ImportanceGroup(
    val code: String,
    val coeffI: Double,
    val returnPeriodYears: Int,
    val nuA: Double,
    val description: String
) {
    GROUP_1A("1A", 1.40, 1178, 0.39, "Bâtiments d'importance vitale"),
    GROUP_1B("1B", 1.20, 777, 0.46, "Bâtiments de grande importance"),
    GROUP_2("2", 1.00, 475, 0.55, "Bâtiments d'importance moyenne"),
    GROUP_3("3", 0.80, 260, 0.69, "Bâtiments de faible importance");

    companion object {
        fun fromCode(code: String): ImportanceGroup {
            val normalized = code.trim().uppercase().removePrefix("GROUP_").removePrefix("GROUPE")
            return entries.firstOrNull {
                it.code.equals(normalized, ignoreCase = true) || it.name == "GROUP_$normalized"
            } ?: throw NormativeDataException(
                "Groupe d'importance '$code' inconnu. Valeurs admises : 1A, 1B, 2, 3 (Tableau 3.10)."
            )
        }
    }
}

/**
 * Tableau 3.18 : les categories (a), (b) et (c) renvoient aux exposants du Tableau 3.17.
 * La categorie (c) impose QF = 1.
 */
enum class PenaltyCategory(val label: String, val description: String) {
    A("(a)", "Pénalités : régularité plan, régularité élévation, minimum 2 niveaux, minimum 3 travées"),
    B("(b)", "Pénalités : régularité plan, régularité élévation, redondance en plan"),
    C("(c)", "Aucune pénalité : QF = 1")
}

/** Tableau 4.2 : coefficient d'accompagnement psi des charges d'exploitation. */
enum class OccupancyCase(val code: String, val description: String, val psi: Double) {
    HABITATION_BUREAUX("1", "Bâtiments d'habitation, bureaux ou assimilés", 0.20),
    PUBLIC_DEBOUT("2a", "Salles d'exposition, de sport, lieux de culte, réunions avec places debout", 0.30),
    PUBLIC_ASSIS("2b", "Salles de classes, restaurants, dortoirs, réunions avec places assises", 0.40),
    ENTREPOTS("3", "Entrepôts, hangars", 0.50),
    ARCHIVES("4", "Archives, bibliothèques, réservoirs et ouvrages assimilés", 1.00),
    AUTRES("5", "Autres locaux non visés ci-dessus", 0.60);

    companion object {
        fun fromCode(code: String): OccupancyCase {
            val normalized = code.trim().uppercase()
            return entries.firstOrNull { it.code.equals(normalized, ignoreCase = true) || it.name == normalized }
                ?: throw NormativeDataException(
                    "Cas d'occupation '$code' inconnu. Valeurs admises : 1, 2a, 2b, 3, 4, 5 (Tableau 4.2)."
                )
        }
    }
}
