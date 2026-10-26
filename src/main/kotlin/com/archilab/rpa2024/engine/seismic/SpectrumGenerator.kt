package com.archilab.rpa2024.engine.seismic

import com.archilab.rpa2024.normative.*
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.max

@Serializable
data class SpectrumCurvePoint(
    val period: Double,
    val saOverG: Double
) {
    val periodT: Double get() = period
}

@Serializable
enum class SpectrumBranchCase(val caseNumber: Int, val description: String) {
    CASE_1_ASCENDING(1, "Branche ascendante [0, T1["),
    CASE_2_PLATEAU(2, "Palier d'accélération constante [T1, T2]"),
    CASE_3_VELOCITY(3, "Branche à vitesse constante [T2, T3]"),
    CASE_4_DISPLACEMENT(4, "Branche à déplacement constant [T3, 4.0s]")
}

/**
 * Générateur officiel des spectres de réponse RPA 2024 (Éq. 3.8, 3.14, 3.15).
 */
class SpectrumGenerator {

    /**
     * Spectre de réponse élastique horizontal Sae(T)/g (§ 3.3.1 - Éq. 3.8).
     */
    fun computeElasticHorizontalSaOverG(
        t: Double,
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        dampingXiPercent: Double = 7.0
    ): Double {
        val params = Rpa2024NormativeDb.getSpectralParameters(zone, site)
        val eta = Rpa2024NormativeDb.computeDampingCorrectionEta(dampingXiPercent)
        val a = zone.coeffA
        val i = importance.coeffI
        val s = params.s
        val t1 = params.t1
        val t2 = params.t2
        val t3 = params.t3

        val base = a * i * s
        return when {
            t <= 0.0 -> base
            t < t1 -> base * (1.0 + (t / t1) * (2.5 * eta - 1.0))
            t <= t2 -> base * (2.5 * eta)
            t <= t3 -> base * (2.5 * eta) * (t2 / t)
            else -> base * (2.5 * eta) * ((t2 * t3) / (t * t))
        }
    }

    /**
     * Spectre de réponse élastique vertical Sav(T)/g (§ 3.3.2 - Éq. 3.14).
     */
    fun computeElasticVerticalSaOverG(
        t: Double,
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        dampingXiPercent: Double = 7.0
    ): Double {
        val params = Rpa2024NormativeDb.getSpectralParameters(zone, site)
        val eta = Rpa2024NormativeDb.computeDampingCorrectionEta(dampingXiPercent)
        val av = 0.70 * zone.coeffA // Rapport d'accélération verticale Av/A
        val i = importance.coeffI
        val t1 = params.t1
        val t2 = params.t2
        val t3 = params.t3

        val base = av * i
        return when {
            t <= 0.0 -> base
            t < t1 -> base * (1.0 + (t / t1) * (2.5 * eta - 1.0))
            t <= t2 -> base * (2.5 * eta)
            t <= t3 -> base * (2.5 * eta) * (t2 / t)
            else -> base * (2.5 * eta) * ((t2 * t3) / (t * t))
        }
    }

    /**
     * Spectre de calcul (dimensionnement) horizontal Sad(T)/g (§ 3.3.3 - Éq. 3.15).
     * Inclut le plancher réglementaire obligatoire 0.20 * A * I.
     */
    fun computeDesignHorizontalSaOverG(
        t: Double,
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        bracing: BracingSystem,
        qualityFactorQf: Double,
        dampingXiPercent: Double = 7.0
    ): Pair<Double, SpectrumBranchCase> {
        val params = Rpa2024NormativeDb.getSpectralParameters(zone, site)
        val eta = Rpa2024NormativeDb.computeDampingCorrectionEta(dampingXiPercent)
        val a = zone.coeffA
        val i = importance.coeffI
        val s = params.s
        val t1 = params.t1
        val t2 = params.t2
        val t3 = params.t3
        val r = bracing.coeffR

        val minCutoff = 0.20 * a * i
        val plateau = (a * i * s) * (2.5 * eta / r) * qualityFactorQf

        val (raw, branch) = when {
            t <= 0.0 -> Pair((a * i * s) * (2.0 / 3.0), SpectrumBranchCase.CASE_1_ASCENDING)
            t < t1 -> Pair(
                (a * i * s) * ((2.0 / 3.0) + (t / t1) * ((2.5 * eta / r) * qualityFactorQf - (2.0 / 3.0))),
                SpectrumBranchCase.CASE_1_ASCENDING
            )
            t <= t2 -> Pair(plateau, SpectrumBranchCase.CASE_2_PLATEAU)
            t <= t3 -> Pair(plateau * (t2 / t), SpectrumBranchCase.CASE_3_VELOCITY)
            else -> Pair(plateau * ((t2 * t3) / (t * t)), SpectrumBranchCase.CASE_4_DISPLACEMENT)
        }

        return Pair(max(minCutoff, raw), branch)
    }

    /**
     * Génère la courbe spectrale complète discrétisée entre 0.0s et maxT (ex: 4.0s).
     */
    fun generateSpectrumCurve(
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        bracing: BracingSystem,
        qualityFactorQf: Double,
        step: Double = 0.02,
        maxT: Double = 4.0,
        isHorizontal: Boolean = true
    ): List<SpectrumCurvePoint> {
        val points = mutableListOf<SpectrumCurvePoint>()
        var t = 0.0
        while (t <= maxT + 1e-6) {
            val sa = if (isHorizontal) {
                computeDesignHorizontalSaOverG(t, zone, site, importance, bracing, qualityFactorQf).first
            } else {
                computeElasticVerticalSaOverG(t, zone, site, importance)
            }
            points.add(SpectrumCurvePoint(period = t, saOverG = sa))
            t += step
        }
        return points
    }

    /**
     * Exporte la fonction spectrale dans un fichier texte standard ETABS (.txt).
     * Deux colonnes : Période (sec) et Accélération (g).
     */
    fun exportSpectrumToEtabsFile(
        file: File,
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        bracing: BracingSystem,
        qualityFactorQf: Double,
        step: Double = 0.02,
        isHorizontal: Boolean = true
    ) {
        val points = generateSpectrumCurve(zone, site, importance, bracing, qualityFactorQf, step, isHorizontal = isHorizontal)
        file.bufferedWriter().use { writer ->
            writer.write("; RPA 2024 SPECTRUM - ARCHILAB EXPORT\n")
            writer.write("; Zone: ${zone.code}, Site: ${site.code}, Group: ${importance.code}, R: ${bracing.coeffR}, Qf: $qualityFactorQf\n")
            writer.write("; Period(s)\tSa/g\n")
            for (p in points) {
                writer.write(String.format(java.util.Locale.US, "%.4f\t%.6f\n", p.period, p.saOverG))
            }
        }
    }

    fun calculateMinSpectrumThreshold(params: RpaSpectrumParams): Double =
        0.20 * params.zoneAccelerationA * params.importanceFactorI

    fun generateDesignSpectrumPoints(
        params: RpaSpectrumParams,
        maxPeriodSeconds: Double = 4.0,
        numPoints: Int = 120
    ): List<SpectrumCurvePoint> {
        val step = maxPeriodSeconds / numPoints
        val points = mutableListOf<SpectrumCurvePoint>()
        var t = 0.0
        while (t <= maxPeriodSeconds + 1e-6) {
            val (sa, _) = computeDesignHorizontalSaOverG(
                t = t,
                zone = params.zone,
                site = params.site,
                importance = params.importance,
                bracing = params.bracing,
                qualityFactorQf = params.qualityFactorQf,
                dampingXiPercent = params.dampingXiPercent
            )
            points.add(SpectrumCurvePoint(period = t, saOverG = sa))
            t += step
        }
        return points
    }
}
