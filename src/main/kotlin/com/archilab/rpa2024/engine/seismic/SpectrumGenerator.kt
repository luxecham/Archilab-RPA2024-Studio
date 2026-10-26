package com.archilab.rpa2024.engine.seismic

import com.archilab.rpa2024.normative.ResolvedSeismicConfig
import com.archilab.rpa2024.normative.SiteClass
import com.archilab.rpa2024.normative.SpectralParams
import com.archilab.rpa2024.normative.SpectrumType
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Les quatre spectres utilises par la note de calcul RPA 2024. */
enum class SpectrumKind(val label: String) {
    HORIZONTAL_ELASTIC("Spectre élastique horizontal Sae/g (§ 3.3.1)"),
    HORIZONTAL_DESIGN("Spectre de calcul horizontal Sad/g (§ 3.3.3)"),
    VERTICAL_ELASTIC("Spectre élastique vertical Sve/g (§ 3.3.2)"),
    VERTICAL_DESIGN("Spectre de calcul vertical Svd/g (§ 3.3.3)")
}

/** Branche spectrale effectivement utilisee pour une periode donnee. */
enum class SpectralBranch(val label: String) {
    CASE_1("Cas 1 : [0, T1["),
    CASE_2("Cas 2 : [T1, T2["),
    CASE_3("Cas 3 : [T2, T3["),
    CASE_4("Cas 4 : [T3, 4 s]"),
    OUT_OF_RANGE("Hors domaine : T > 4 s")
}

/** Parametres du spectre vertical (Tableaux 3.7 et 3.8 du RPA 2024). */
data class VerticalSpectrumParameters(
    val avOverA: Double,
    val av: Double,
    val t1Seconds: Double,
    val t2Seconds: Double,
    val t3Seconds: Double,
    val alpha: Double,
    val reference: String
)

/** Point echantillonne d'un spectre, acceleration normalisee par g. */
data class SpectrumPoint(
    val periodSeconds: Double,
    val accelerationOverG: Double,
    val branch: SpectralBranch
)

/** Courbe spectrale echantillonnee, prete pour le trace ou l'export ETABS. */
data class SpectrumCurve(
    val kind: SpectrumKind,
    val points: List<SpectrumPoint>,
    val t1Seconds: Double,
    val t2Seconds: Double,
    val t3Seconds: Double,
    val plateauOverG: Double,
    val floorOverG: Double? = null,
    val parametersLabel: String = ""
) {
    val maxPeriodSeconds: Double get() = points.maxOfOrNull { it.periodSeconds } ?: 0.0

    /** Interpolation lineaire entre deux points echantillonnes. */
    fun valueAt(periodSeconds: Double): Double {
        if (points.isEmpty()) return 0.0
        if (periodSeconds <= points.first().periodSeconds) return points.first().accelerationOverG
        if (periodSeconds >= points.last().periodSeconds) return points.last().accelerationOverG
        val upperIndex = points.indexOfFirst { it.periodSeconds >= periodSeconds }
        if (upperIndex <= 0) return points.first().accelerationOverG
        val lower = points[upperIndex - 1]
        val upper = points[upperIndex]
        val width = upper.periodSeconds - lower.periodSeconds
        if (width <= 0.0) return upper.accelerationOverG
        val ratio = (periodSeconds - lower.periodSeconds) / width
        return lower.accelerationOverG + ratio * (upper.accelerationOverG - lower.accelerationOverG)
    }
}

/** Jeu de spectres exporte : spectres de calcul ou spectres elastiques. */
enum class SpectrumSet(val label: String) {
    DESIGN("Spectres de calcul"),
    ELASTIC("Spectres élastiques")
}

/** Chemins et metadonnees des deux fichiers ETABS produits. */
data class SpectrumExportResult(
    val horizontalPath: Path,
    val verticalPath: Path,
    val horizontalPointCount: Int,
    val verticalPointCount: Int,
    val stepSeconds: Double,
    val spectrumSet: SpectrumSet,
    val comment: String
)

/**
 * Generateur des spectres de reponse du RPA 2024 (DTR BC 2.48) :
 * - spectre elastique horizontal, Eq. (3.8) ;
 * - spectre elastique vertical, Eq. (3.14) avec les coefficients alpha des Tableaux 3.7/3.8 ;
 * - spectre de calcul horizontal, Eq. (3.15), plancher obligatoire 0,20.A.I ;
 * - spectre de calcul vertical, Eq. (3.16), S = 1, QF = 1.
 *
 * Les valeurs retournees sont normalisees par g et peuvent etre importees directement
 * dans ETABS comme fonction de reponse spectrale.
 */
class SpectrumGenerator(
    val spectralParams: SpectralParams,
    val zoneAcceleration: Double,
    val importanceFactor: Double,
    val behaviorFactor: Double,
    val qualityFactor: Double,
    val eta: Double = 1.0
) {
    init {
        require(zoneAcceleration >= 0.0) { "Le coefficient d'accélération A doit être positif." }
        require(importanceFactor > 0.0) { "Le coefficient d'importance I doit être positif." }
        require(behaviorFactor > 0.0) { "Le coefficient de comportement R doit être positif." }
        require(qualityFactor > 0.0) { "Le facteur de qualité QF doit être positif." }
        require(eta > 0.0) { "Le coefficient d'amortissement η doit être positif." }
    }

    private val baseHorizontal: Double
        get() = zoneAcceleration * importanceFactor * spectralParams.s

    private val designFloor: Double
        get() = 0.2 * zoneAcceleration * importanceFactor

    /** Parametres verticaux deduits du type de spectre, du site et des Tableaux 3.7/3.8. */
    val verticalParameters: VerticalSpectrumParameters by lazy {
        val type1 = spectralParams.spectrumType == SpectrumType.TYPE_1
        val t2 = when (spectralParams.siteClass) {
            SiteClass.S1 -> if (type1) 0.20 else 0.15
            SiteClass.S2 -> if (type1) 0.30 else 0.20
            SiteClass.S3 -> if (type1) 0.40 else 0.25
            SiteClass.S4 -> if (type1) 0.50 else 0.30
            SiteClass.SS -> throw IllegalArgumentException(
                "Site SS : aucun spectre vertical tabulé, étude spécifique requise."
            )
        }
        VerticalSpectrumParameters(
            avOverA = if (type1) 0.90 else 0.55,
            av = zoneAcceleration * if (type1) 0.90 else 0.55,
            t1Seconds = 0.05,
            t2Seconds = t2,
            t3Seconds = 1.0,
            alpha = if (type1) 0.6 else 0.8,
            reference = if (type1) "Tableau 3.7" else "Tableau 3.8"
        )
    }

    /** Spectre elastique horizontal Sae/g, Eq. (3.8). */
    fun horizontalElastic(periodSeconds: Double): Double {
        val t = max(0.0, periodSeconds)
        val plateau = 2.5 * eta
        val a = baseHorizontal
        return when {
            t < spectralParams.t1 -> a * (1.0 + t / spectralParams.t1 * (plateau - 1.0))
            t < spectralParams.t2 -> a * plateau
            t < spectralParams.t3 -> a * plateau * (spectralParams.t2 / t)
            t <= 4.0 -> a * plateau * (spectralParams.t2 * spectralParams.t3 / (t * t))
            else -> 0.0
        }
    }

    /** Spectre de calcul horizontal Sad/g, Eq. (3.15), avec plancher 0,20.A.I. */
    fun horizontalDesign(periodSeconds: Double): Double {
        val t = max(0.0, periodSeconds)
        val plateau = 2.5 * qualityFactor / behaviorFactor
        val a = baseHorizontal
        val value = when {
            t < spectralParams.t1 -> a * (2.0 / 3.0 + t / spectralParams.t1 * (plateau - 2.0 / 3.0))
            t < spectralParams.t2 -> a * plateau
            t < spectralParams.t3 -> a * plateau * (spectralParams.t2 / t)
            t <= 4.0 -> a * plateau * (spectralParams.t2 * spectralParams.t3 / (t * t))
            else -> 0.0
        }
        return max(value, designFloor)
    }

    /** Spectre elastique vertical Sve/g, Eq. (3.14). */
    fun verticalElastic(periodSeconds: Double): Double {
        val t = max(0.0, periodSeconds)
        val vertical = verticalParameters
        val a = vertical.av * importanceFactor
        val plateau = 2.5 * eta
        return when {
            t < vertical.t1Seconds -> a * (1.0 + t / vertical.t1Seconds * (plateau - 1.0))
            t < vertical.t2Seconds -> a * plateau
            t < vertical.t3Seconds -> a * plateau * (vertical.t2Seconds / t).pow(vertical.alpha)
            t <= 4.0 -> a * plateau *
                ((vertical.t2Seconds * vertical.t3Seconds) / (t * t)).pow(vertical.alpha)
            else -> 0.0
        }
    }

    /** Spectre de calcul vertical Svd/g, Eq. (3.16), S = 1 et QF = 1. */
    fun verticalDesign(periodSeconds: Double): Double {
        val t = max(0.0, periodSeconds)
        val vertical = verticalParameters
        val a = vertical.av * importanceFactor
        val plateau = 2.5 / behaviorFactor
        return when {
            t < vertical.t1Seconds -> a * (2.0 / 3.0 + t / vertical.t1Seconds * (plateau - 2.0 / 3.0))
            t < vertical.t2Seconds -> a * plateau
            t < vertical.t3Seconds -> a * plateau * (vertical.t2Seconds / t).pow(vertical.alpha)
            t <= 4.0 -> a * plateau *
                ((vertical.t2Seconds * vertical.t3Seconds) / (t * t)).pow(vertical.alpha)
            else -> 0.0
        }
    }

    fun valueAt(kind: SpectrumKind, periodSeconds: Double): Double = when (kind) {
        SpectrumKind.HORIZONTAL_ELASTIC -> horizontalElastic(periodSeconds)
        SpectrumKind.HORIZONTAL_DESIGN -> horizontalDesign(periodSeconds)
        SpectrumKind.VERTICAL_ELASTIC -> verticalElastic(periodSeconds)
        SpectrumKind.VERTICAL_DESIGN -> verticalDesign(periodSeconds)
    }

    /** Numero de la branche spectrale, conforme a la numerotation du RPA 2024. */
    fun branchAt(kind: SpectrumKind, periodSeconds: Double): SpectralBranch {
        val vertical = kind == SpectrumKind.VERTICAL_ELASTIC || kind == SpectrumKind.VERTICAL_DESIGN
        val t1 = if (vertical) verticalParameters.t1Seconds else spectralParams.t1
        val t2 = if (vertical) verticalParameters.t2Seconds else spectralParams.t2
        val t3 = if (vertical) verticalParameters.t3Seconds else spectralParams.t3
        val t = max(0.0, periodSeconds)
        return when {
            t < t1 -> SpectralBranch.CASE_1
            t < t2 -> SpectralBranch.CASE_2
            t < t3 -> SpectralBranch.CASE_3
            t <= 4.0 -> SpectralBranch.CASE_4
            else -> SpectralBranch.OUT_OF_RANGE
        }
    }

    /** Echantillonne une branche spectrale entre 0 et 4 s. */
    fun sample(kind: SpectrumKind, step: Double = 0.02, maxPeriodSeconds: Double = 4.0): SpectrumCurve {
        require(step > 0.0) { "Le pas d'échantillonnage doit être positif." }
        require(maxPeriodSeconds > 0.0) { "La période maximale doit être positive." }
        val count = ceil(maxPeriodSeconds / step).toInt()
        val points = (0..count).map { index ->
            val period = min(index * step, maxPeriodSeconds)
            SpectrumPoint(period, valueAt(kind, period), branchAt(kind, period))
        }.distinctBy { it.periodSeconds }

        val vertical = kind == SpectrumKind.VERTICAL_ELASTIC || kind == SpectrumKind.VERTICAL_DESIGN
        val t1 = if (vertical) verticalParameters.t1Seconds else spectralParams.t1
        val t2 = if (vertical) verticalParameters.t2Seconds else spectralParams.t2
        val t3 = if (vertical) verticalParameters.t3Seconds else spectralParams.t3
        val plateau = when (kind) {
            SpectrumKind.HORIZONTAL_ELASTIC -> baseHorizontal * 2.5 * eta
            SpectrumKind.HORIZONTAL_DESIGN -> baseHorizontal * 2.5 * qualityFactor / behaviorFactor
            SpectrumKind.VERTICAL_ELASTIC -> verticalParameters.av * importanceFactor * 2.5 * eta
            SpectrumKind.VERTICAL_DESIGN -> verticalParameters.av * importanceFactor * 2.5 / behaviorFactor
        }
        return SpectrumCurve(
            kind = kind,
            points = points,
            t1Seconds = t1,
            t2Seconds = t2,
            t3Seconds = t3,
            plateauOverG = plateau,
            floorOverG = if (kind == SpectrumKind.HORIZONTAL_DESIGN) designFloor else null,
            parametersLabel = parametersLabel()
        )
    }

    /**
     * Exporte les fichiers `Spectre_horizontal.txt` et `Spectre_vertical.txt` au format
     * numerique a deux colonnes (periode en s, acceleration en g), directement importables
     * comme fonction "Response Spectrum" dans ETABS.
     *
     * @param filePath repertoire de destination ou chemin de fichier indicateur.
     * @param step pas d'echantillonnage en secondes (0,02 s par defaut).
     * @param spectrumSet spectres de calcul (par defaut) ou spectres elastiques.
     */
    fun exportSpectrumToEtabsFile(
        filePath: Path,
        step: Double = 0.02,
        spectrumSet: SpectrumSet = SpectrumSet.DESIGN
    ): SpectrumExportResult {
        require(step > 0.0) { "Le pas d'échantillonnage doit être positif." }
        val directory = destinationDirectory(filePath)
        Files.createDirectories(directory)

        val horizontalKind = if (spectrumSet == SpectrumSet.DESIGN) {
            SpectrumKind.HORIZONTAL_DESIGN
        } else {
            SpectrumKind.HORIZONTAL_ELASTIC
        }
        val verticalKind = if (spectrumSet == SpectrumSet.DESIGN) {
            SpectrumKind.VERTICAL_DESIGN
        } else {
            SpectrumKind.VERTICAL_ELASTIC
        }
        val horizontalCurve = sample(horizontalKind, step)
        val verticalCurve = sample(verticalKind, step)
        val horizontalPath = directory.resolve("Spectre_horizontal.txt")
        val verticalPath = directory.resolve("Spectre_vertical.txt")
        writeCurve(horizontalPath, horizontalCurve)
        writeCurve(verticalPath, verticalCurve)

        return SpectrumExportResult(
            horizontalPath = horizontalPath,
            verticalPath = verticalPath,
            horizontalPointCount = horizontalCurve.points.size,
            verticalPointCount = verticalCurve.points.size,
            stepSeconds = step,
            spectrumSet = spectrumSet,
            comment = "Accélérations normalisées par g : importer comme fonction de réponse spectrale " +
                "dans ETABS, unité d'accélération = g."
        )
    }

    /** Ecrit une courbe spectrale unique au format ETABS (deux colonnes numériques). */
    fun writeSpectrumFile(path: Path, kind: SpectrumKind, step: Double = 0.02): Path {
        require(step > 0.0) { "Le pas d'échantillonnage doit être positif." }
        destinationDirectory(path).let { directory -> Files.createDirectories(directory) }
        writeCurve(path, sample(kind, step))
        return path
    }

    private fun writeCurve(path: Path, curve: SpectrumCurve) {
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            curve.points.forEach { point ->
                writer.write(
                    String.format(
                        Locale.ROOT,
                        "%.5f,%.6f%n",
                        point.periodSeconds,
                        point.accelerationOverG
                    )
                )
            }
        }
    }

    private fun destinationDirectory(filePath: Path): Path {
        val absolute = filePath.toAbsolutePath()
        return when {
            Files.isDirectory(absolute) -> absolute
            absolute.fileName?.toString()?.contains('.') == true -> absolute.parent ?: absolute
            else -> absolute
        }
    }

    private fun parametersLabel(): String =
        "Zone ${zoneLabel()}, site ${spectralParams.siteClass.code}, A=${format(zoneAcceleration)}, " +
            "I=${format(importanceFactor)}, S=${format(spectralParams.s)}, R=${format(behaviorFactor)}, " +
            "QF=${format(qualityFactor)}, η=${format(eta)}"

    private fun zoneLabel(): String = when (zoneAcceleration) {
        0.0 -> "0"
        0.07 -> "I"
        0.10 -> "II"
        0.15 -> "III"
        0.20 -> "IV"
        0.25 -> "V"
        0.30 -> "VI"
        else -> format(zoneAcceleration)
    }

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    companion object {
        /** Construit le generateur depuis la configuration sismique resolue du joint. */
        fun from(resolved: ResolvedSeismicConfig): SpectrumGenerator {
            val params = requireNotNull(resolved.spectralParams) {
                "Aucun spectre tabulé disponible pour le site ${resolved.siteClass.code}."
            }
            return SpectrumGenerator(
                spectralParams = params,
                zoneAcceleration = resolved.zone.coeffA,
                importanceFactor = resolved.importance.coeffI,
                behaviorFactor = resolved.behaviorFactorR,
                qualityFactor = resolved.qualityFactor.qf,
                eta = resolved.eta
            )
        }
    }
}
