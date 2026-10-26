package com.archilab.rpa2024.parser

/**
 * Résolveur d'alias de colonnes unifiant les variations de libellés
 * entre ETABS v9.7.4 et les versions récentes (ETABS v18 / v21).
 */
object ColumnAliasResolver {

    private val ALIAS_MAP = mapOf(
        "mode" to listOf("mode", "stepnum", "modal", "modenumber", "item"),
        "period" to listOf("period", "t (sec)", "t(sec)", "sec", "time", "period (sec)", "période"),
        "ux" to listOf("ux", "ux (%)", "modal ux", "ux-trans"),
        "uy" to listOf("uy", "uy (%)", "modal uy", "uy-trans"),
        "uz" to listOf("uz", "uz (%)", "modal uz"),
        "sumux" to listOf("sumux", "sum ux", "sum ux (%)", "cumux", "somme ux"),
        "sumuy" to listOf("sumuy", "sum uy", "sum uy (%)", "cumuy", "somme uy"),
        "sumuz" to listOf("sumuz", "sum uz", "sum uz (%)"),
        "rx" to listOf("rx", "modal rx", "rot x"),
        "ry" to listOf("ry", "modal ry", "rot y"),
        "rz" to listOf("rz", "modal rz", "rot z"),
        "story" to listOf("story", "story level", "etage", "étage", "niveau"),
        "load" to listOf("load", "load case/combo", "combo", "output case", "case", "combinaison"),
        "loc" to listOf("loc", "location", "position", "station"),
        "p" to listOf("p", "p (kn)", "force p", "axial force", "n", "effort normal"),
        "v2" to listOf("v2", "v2 (kn)", "shear v2", "vy", "f2"),
        "v3" to listOf("v3", "v3 (kn)", "shear v3", "vx", "f1"),
        "m2" to listOf("m2", "m2 (kn-m)", "moment m2"),
        "m3" to listOf("m3", "m3 (kn-m)", "moment m3"),
        "vx" to listOf("vx", "vx (kn)", "f1", "fx", "base shear x"),
        "vy" to listOf("vy", "vy (kn)", "f2", "fy", "base shear y"),
        "diaphragm" to listOf("diaphragm", "diaphragme", "dia"),
        "column" to listOf("column", "col", "poteau", "frame", "element", "line"),
        "beam" to listOf("beam", "poutre", "frame", "element", "line"),
        "pier" to listOf("pier", "voile", "pier name", "wall", "trumeau")
    )

    /**
     * Normalise un nom de colonne brut (supprime espaces, ponctuation superflue et met en minuscule).
     */
    fun canonicalize(header: String): String {
        val clean = header.trim().lowercase().replace("_", " ").replace("-", " ")
        for ((canonical, aliases) in ALIAS_MAP) {
            for (alias in aliases) {
                if (clean == alias || clean.startsWith("$alias ") || clean.contains(" $alias")) {
                    return canonical
                }
            }
        }
        return clean
    }

    /**
     * Recherche la valeur dans une ligne de données indexée par des colonnes brutes.
     */
    fun getValue(row: Map<String, String>, canonicalName: String): String? {
        // Recherche exacte d'abord
        row[canonicalName]?.let { return it }

        // Recherche par correspondance canonique
        for ((key, value) in row) {
            if (canonicalize(key) == canonicalName) {
                return value
            }
        }

        // Recherche dans les alias
        val aliases = ALIAS_MAP[canonicalName] ?: emptyList()
        for ((key, value) in row) {
            val lower = key.trim().lowercase()
            if (aliases.any { lower.contains(it) }) {
                return value
            }
        }

        return null
    }

    fun getDouble(row: Map<String, String>, canonicalName: String, default: Double = 0.0): Double {
        val str = getValue(row, canonicalName) ?: return default
        return str.replace(",", ".").replace(" ", "").toDoubleOrNull() ?: default
    }

    fun getInt(row: Map<String, String>, canonicalName: String, default: Int = 0): Int {
        val str = getValue(row, canonicalName) ?: return default
        return str.replace(" ", "").toIntOrNull() ?: default
    }
}
