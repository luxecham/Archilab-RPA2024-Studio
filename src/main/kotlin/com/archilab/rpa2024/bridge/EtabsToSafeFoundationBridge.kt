package com.archilab.rpa2024.bridge

import com.archilab.rpa2024.engine.foundations.FootingType
import com.archilab.rpa2024.engine.foundations.FoundationSoilConfig
import com.archilab.rpa2024.engine.foundations.StripFootingSection
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.GridLine
import com.archilab.rpa2024.model.StructuralJoint
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * Réaction d'appui ponctuelle à la base (Z = 0) issue d'un poteau ou d'un trumeau ETABS.
 */
data class BaseReactionPoint(
    val pointLabel: String,
    val xMeters: Double,
    val yMeters: Double,
    val columnOrWallName: String,
    val fzDeadKN: Double,
    val fzLiveKN: Double,
    val fzSeismicExKN: Double = 0.0,
    val fzSeismicEyKN: Double = 0.0,
    val mxDeadKNm: Double = 0.0,
    val mxLiveKNm: Double = 0.0,
    val myDeadKNm: Double = 0.0,
    val myLiveKNm: Double = 0.0,
    val vxDeadKN: Double = 0.0,
    val vyDeadKN: Double = 0.0
) {
    /** Effort axial total de service ELS (G + Q) en kN */
    val nSerKN: Double
        get() = abs(fzDeadKN) + abs(fzLiveKN)

    /** Effort axial total ultime ELU (1.35G + 1.5Q) en kN */
    val nUluKN: Double
        get() = 1.35 * abs(fzDeadKN) + 1.50 * abs(fzLiveKN)
}

/**
 * Ligne de semelle filante SAFE modélisée le long d'une file d'axe.
 */
data class SafeFootingLine(
    val lineId: String,
    val gridAxisId: String,
    val direction: AxisDirection,
    val startPointId: String,
    val endPointId: String,
    val lengthMeters: Double,
    var footingSection: StripFootingSection,
    var lineSpringStiffnessKNmPerM: Double,
    val tributaryNserKN: Double,
    var calculatedStressKNm2: Double,
    var isCompliant: Boolean
)

/**
 * Rapport d'optimisation et de génération du pont ETABS -> SAFE.
 */
data class SafeBridgeReport(
    val success: Boolean,
    val iterationsCount: Int,
    val initialMaxStressKNm2: Double,
    val finalMaxStressKNm2: Double,
    val allowableStressKNm2: Double,
    val footingWidths: Map<String, Double>,
    val lineSprings: Map<String, Double>,
    val s2kContent: String,
    val diagnosticMessages: List<String>
)

/**
 * Pont automatique Superstructure ETABS -> Fondations SAFE.
 * Génère le modèle SAFE complet (.s2k), convertit la grille et les réactions,
 * applique le béton différé BETON25 (E_vj = 10 818 000 kN/m²), les ressorts de sol
 * non-linéaires en compression seule K_line = Ks * B, et optimise la largeur des semelles.
 */
class EtabsToSafeFoundationBridge {

    /**
     * Module de déformation différée du béton BETON25 selon CBA 93 / BAEL 91 :
     * E_vj = 3700 * fc28^(1/3) = 3700 * 25^(1/3) = 10 818 000 kN/m² (10 818 MPa)
     * nu = 0.20
     * G = E_vj / (2 * (1 + nu)) = 4 507 500 kN/m²
     */
    val safeConcreteEvjKNm2: Double = 10818000.0
    val safeConcretePoisson: Double = 0.20
    val safeConcreteGKNm2: Double = safeConcreteEvjKNm2 / (2.0 * (1.0 + safeConcretePoisson)) // 4 507 500
    val safeConcreteUnitWeightKNm3: Double = 25.0

    /**
     * Acier de ferraillage FE500.
     */
    val safeSteelEsKNm2: Double = 210000000.0
    val safeSteelFyKNm2: Double = 500000.0
    val safeSteelFuKNm2: Double = 550000.0

    /**
     * Génère automatiquement les réactions d'appuis de base représentatives à partir
     * de la topologie géométrique du Joint si aucune réaction explicite n'est injectée.
     */
    fun deriveBaseReactionsFromTopology(joint: StructuralJoint): List<BaseReactionPoint> {
        val reactions = mutableListOf<BaseReactionPoint>()
        val xGrids = joint.gridSystem.sortedXGrids
        val yGrids = joint.gridSystem.sortedYGrids
        val nStories = joint.storySystem.numStoriesN

        var ptIdx = 1
        for (xg in xGrids) {
            for (yg in yGrids) {
                // Détection si c'est un poteau de rive ou intérieur
                val isEdgeX = xg == xGrids.first() || xg == xGrids.last()
                val isEdgeY = yg == yGrids.first() || yg == yGrids.last()

                val tributaryArea = when {
                    isEdgeX && isEdgeY -> 9.0   // Angle (3m x 3m)
                    isEdgeX || isEdgeY -> 18.0  // Rive (3m x 6m)
                    else -> 25.0                // Centre (5m x 5m)
                }

                // Charge permanente G ~ 10 kN/m² par étage
                // Charge d'exploitation Q ~ 2.5 kN/m² par étage
                val fzDead = -1.0 * (tributaryArea * 10.0 * nStories)
                val fzLive = -1.0 * (tributaryArea * 2.5 * nStories)
                val fzSeismic = tributaryArea * 1.8 * nStories

                val label = "P_${xg.id}_${yg.id}"
                reactions.add(
                    BaseReactionPoint(
                        pointLabel = label,
                        xMeters = xg.coordinate,
                        yMeters = yg.coordinate,
                        columnOrWallName = "COL_${xg.id}_${yg.id}",
                        fzDeadKN = fzDead,
                        fzLiveKN = fzLive,
                        fzSeismicExKN = fzSeismic,
                        fzSeismicEyKN = fzSeismic * 0.85,
                        mxDeadKNm = if (isEdgeX) 15.0 else 5.0,
                        mxLiveKNm = if (isEdgeX) 8.0 else 2.5,
                        myDeadKNm = if (isEdgeY) 18.0 else 6.0,
                        myLiveKNm = if (isEdgeY) 9.0 else 3.0
                    )
                )
                ptIdx++
            }
        }
        return reactions
    }

    /**
     * Exécute le dimensionnement et l'optimisation itérative des largeurs de semelles
     * pour garantir que la contrainte effective au sol reste inférieure à la capacité admissible.
     */
    fun buildAndOptimizeSafeModel(
        joint: StructuralJoint,
        soilConfig: FoundationSoilConfig = FoundationSoilConfig(
            allowableBearingCapacityBars = joint.materials.soilBearingCapacityBars,
            subgradeModulusKsKNM3 = joint.materials.soilSubgradeModulusKNm3
        ),
        baseReactions: List<BaseReactionPoint>? = null
    ): SafeBridgeReport {
        val reactions = baseReactions ?: deriveBaseReactionsFromTopology(joint)
        val xGrids = joint.gridSystem.sortedXGrids
        val yGrids = joint.gridSystem.sortedYGrids
        val ks = soilConfig.subgradeModulusKsKNM3
        val allowableStress = soilConfig.allowableBearingCapacityKNM2 // kPa = kN/m²

        val diagnostics = mutableListOf<String>()
        diagnostics.add("Démarrage du pont ETABS -> SAFE pour le joint: ${joint.jointName}")
        diagnostics.add("Paramètres du sol : Ks = $ks kN/m³, sigma_adm = $allowableStress kN/m² (${soilConfig.allowableBearingCapacityBars} bars)")

        // 1. Définition initiale des semelles filantes le long des axes X et Y
        val footingLines = mutableListOf<SafeFootingLine>()

        // Lignes le long des axes X
        for (yg in yGrids) {
            val length = joint.gridSystem.totalLengthLx
            val totalNser = reactions.filter { abs(it.yMeters - yg.coordinate) < 0.05 }.sumOf { it.nSerKN }
            val isJointEdge = yg == yGrids.first() || yg == yGrids.last()

            val initialType = if (isJointEdge) FootingType.SEISMIC_JOINT_FOOTING else FootingType.REGULAR_FOOTING
            val initialWidth = if (isJointEdge) 1.60 else 1.20
            val initialSectionName = if (isJointEdge) "SFJ160" else "SF120"

            val section = StripFootingSection(
                sectionName = initialSectionName,
                footingType = initialType,
                footingWidthBMeters = initialWidth,
                slabThicknessH0Meters = 0.40,
                ribWidthB0Meters = if (isJointEdge) 0.50 else 0.40,
                totalHeightHMeters = 0.90
            )

            val springK = ks * initialWidth
            val sigma = if (length > 0) totalNser / (initialWidth * length) else 0.0

            footingLines.add(
                SafeFootingLine(
                    lineId = "FTG_Y_${yg.id}",
                    gridAxisId = yg.id,
                    direction = AxisDirection.X,
                    startPointId = "P_${xGrids.first().id}_${yg.id}",
                    endPointId = "P_${xGrids.last().id}_${yg.id}",
                    lengthMeters = length,
                    footingSection = section,
                    lineSpringStiffnessKNmPerM = springK,
                    tributaryNserKN = totalNser,
                    calculatedStressKNm2 = sigma,
                    isCompliant = sigma <= allowableStress
                )
            )
        }

        // Lignes le long des axes Y
        for (xg in xGrids) {
            val length = joint.gridSystem.totalLengthLy
            val totalNser = reactions.filter { abs(it.xMeters - xg.coordinate) < 0.05 }.sumOf { it.nSerKN }
            val isJointEdge = xg == xGrids.first() || xg == xGrids.last()

            val initialType = if (isJointEdge) FootingType.SEISMIC_JOINT_FOOTING else FootingType.REGULAR_FOOTING
            val initialWidth = if (isJointEdge) 1.60 else 1.20
            val initialSectionName = if (isJointEdge) "SFJ160" else "SF120"

            val section = StripFootingSection(
                sectionName = initialSectionName,
                footingType = initialType,
                footingWidthBMeters = initialWidth,
                slabThicknessH0Meters = 0.40,
                ribWidthB0Meters = if (isJointEdge) 0.50 else 0.40,
                totalHeightHMeters = 0.90
            )

            val springK = ks * initialWidth
            val sigma = if (length > 0) totalNser / (initialWidth * length) else 0.0

            footingLines.add(
                SafeFootingLine(
                    lineId = "FTG_X_${xg.id}",
                    gridAxisId = xg.id,
                    direction = AxisDirection.Y,
                    startPointId = "P_${xg.id}_${yGrids.first().id}",
                    endPointId = "P_${xg.id}_${yGrids.last().id}",
                    lengthMeters = length,
                    footingSection = section,
                    lineSpringStiffnessKNmPerM = springK,
                    tributaryNserKN = totalNser,
                    calculatedStressKNm2 = sigma,
                    isCompliant = sigma <= allowableStress
                )
            )
        }

        val initialMaxStress = footingLines.maxOfOrNull { it.calculatedStressKNm2 } ?: 0.0
        diagnostics.add(String.format(Locale.US, "Contrainte maximale initiale : %.2f kN/m² (Limite admissible: %.2f kN/m²)", initialMaxStress, allowableStress))

        // 2. Boucle d'optimisation / élargissement des semelles si dépassement
        var iteration = 1
        val maxIterations = 6
        var hasNonCompliant = footingLines.any { !it.isCompliant }

        while (hasNonCompliant && iteration <= maxIterations) {
            diagnostics.add("--- Itération d'optimisation $iteration ---")
            for (line in footingLines) {
                if (!line.isCompliant) {
                    val currentB = line.footingSection.footingWidthBMeters
                    val newB = round((currentB + 0.20) * 100.0) / 100.0
                    val isJoint = line.footingSection.footingType == FootingType.SEISMIC_JOINT_FOOTING
                    val newName = if (isJoint) "SFJ${(newB * 100).toInt()}" else "SF${(newB * 100).toInt()}"

                    val newSection = line.footingSection.copy(
                        sectionName = newName,
                        footingWidthBMeters = newB
                    )
                    val newSpringK = ks * newB
                    val newStress = if (line.lengthMeters > 0) line.tributaryNserKN / (newB * line.lengthMeters) else 0.0

                    line.footingSection = newSection
                    line.lineSpringStiffnessKNmPerM = newSpringK
                    line.calculatedStressKNm2 = newStress
                    line.isCompliant = newStress <= allowableStress

                    diagnostics.add(
                        String.format(
                            Locale.US,
                            "Ligne %s élargie : B = %.2fm -> %.2fm (%s), sigma = %.2f kN/m², K_line = %.1f kN/m/ml (Conforme: %b)",
                            line.lineId, currentB, newB, newName, newStress, newSpringK, line.isCompliant
                        )
                    )
                }
            }
            hasNonCompliant = footingLines.any { !it.isCompliant }
            iteration++
        }

        val finalMaxStress = footingLines.maxOfOrNull { it.calculatedStressKNm2 } ?: 0.0
        val isAllCompliant = footingLines.all { it.isCompliant }
        diagnostics.add(String.format(Locale.US, "Optimisation terminée. Contrainte finale maximale: %.2f kN/m² (Conformité globale: %b)", finalMaxStress, isAllCompliant))

        // 3. Génération du fichier complet SAFE .s2k
        val s2kContent = generateS2kFileContent(
            joint = joint,
            soilConfig = soilConfig,
            footingLines = footingLines,
            baseReactions = reactions
        )

        val widthsMap = footingLines.associate { it.lineId to it.footingSection.footingWidthBMeters }
        val springsMap = footingLines.associate { it.lineId to it.lineSpringStiffnessKNmPerM }

        return SafeBridgeReport(
            success = isAllCompliant,
            iterationsCount = iteration - 1,
            initialMaxStressKNm2 = initialMaxStress,
            finalMaxStressKNm2 = finalMaxStress,
            allowableStressKNm2 = allowableStress,
            footingWidths = widthsMap,
            lineSprings = springsMap,
            s2kContent = s2kContent,
            diagnosticMessages = diagnostics
        )
    }

    /**
     * Génère la chaîne de texte conforme au format officiel CSI SAFE (.s2k).
     */
    fun generateS2kFileContent(
        joint: StructuralJoint,
        soilConfig: FoundationSoilConfig,
        footingLines: List<SafeFootingLine>,
        baseReactions: List<BaseReactionPoint>
    ): String {
        val sb = StringBuilder()

        // 1. En-tête du programme
        sb.appendLine("\$ PROGRAM INFORMATION")
        sb.appendLine("  PROGRAM \"SAFE\"  VERSION \"12.3.0\"")
        sb.appendLine()

        // 2. Unités
        sb.appendLine("\$ UNITS")
        sb.appendLine("  UNITS \"KN-M-C\"")
        sb.appendLine()

        // 3. Grille d'axes
        sb.appendLine("\$ COORDINATE SYSTEMS")
        sb.appendLine("  SYSTEM \"GLOBAL\"  TYPE \"CARTESIAN\"")
        sb.appendLine()

        sb.appendLine("\$ GRID LINES")
        for (xg in joint.gridSystem.sortedXGrids) {
            sb.appendLine(String.format(Locale.US, "  GRIDLINE SYSTEM \"GLOBAL\"  DIR \"X\"  ID \"%s\"  ORD %.4f", xg.id, xg.coordinate))
        }
        for (yg in joint.gridSystem.sortedYGrids) {
            sb.appendLine(String.format(Locale.US, "  GRIDLINE SYSTEM \"GLOBAL\"  DIR \"Y\"  ID \"%s\"  ORD %.4f", yg.id, yg.coordinate))
        }
        sb.appendLine()

        // 4. Propriétés des matériaux
        sb.appendLine("\$ MATERIAL PROPERTIES")
        // BETON25 différé
        sb.appendLine("  MATERIAL \"BETON25\"  TYPE \"CONCRETE\"")
        sb.appendLine(String.format(Locale.US, "  MATERIAL \"BETON25\"  E %.0f  U %.2f  A 0.000010  G %.0f", safeConcreteEvjKNm2, safeConcretePoisson, safeConcreteGKNm2))
        sb.appendLine(String.format(Locale.US, "  MATERIAL \"BETON25\"  UNITWT %.2f  UNITMASS %.4f", safeConcreteUnitWeightKNm3, safeConcreteUnitWeightKNm3 / 9.81))
        sb.appendLine("  MATERIAL \"BETON25\"  FC 25000")
        // FE500
        sb.appendLine("  MATERIAL \"FE500\"  TYPE \"REBAR\"")
        sb.appendLine(String.format(Locale.US, "  MATERIAL \"FE500\"  E %.0f  FY %.0f  FU %.0f", safeSteelEsKNm2, safeSteelFyKNm2, safeSteelFuKNm2))
        sb.appendLine()

        // 5. Propriétés des poutres (Semelles filantes en Té renversé)
        sb.appendLine("\$ BEAM PROPERTIES")
        val distinctSections = footingLines.map { it.footingSection }.distinctBy { it.sectionName }
        for (sec in distinctSections) {
            sb.appendLine(
                String.format(
                    Locale.US,
                    "  BEAMPROP \"%s\"  SHAPE \"TEE\"  MATERIAL \"BETON25\"  TOTALDEPTH %.2f  FLANGEWIDTH %.2f  FLANGETHICK %.2f  WEBTHICK %.2f",
                    sec.sectionName, sec.totalHeightHMeters, sec.footingWidthBMeters, sec.slabThicknessH0Meters, sec.ribWidthB0Meters
                )
            )
        }
        sb.appendLine()

        // 6. Ressorts de sol linéaires (Line Springs - Winkler Compression Only)
        sb.appendLine("\$ LINE SPRINGS")
        for (sec in distinctSections) {
            val springName = "K_${sec.sectionName}"
            val kLine = soilConfig.subgradeModulusKsKNM3 * sec.footingWidthBMeters
            sb.appendLine(
                String.format(
                    Locale.US,
                    "  LINESPRING \"%s\"  STIFFNESS %.2f  NONLINEAR TENSION=NO COMPRESSION=YES",
                    springName, kLine
                )
            )
        }
        sb.appendLine()

        // 7. Objets Points (Nœuds d'appuis de fondation)
        sb.appendLine("\$ POINT OBJECTS")
        for (pt in baseReactions) {
            sb.appendLine(
                String.format(
                    Locale.US,
                    "  POINT \"%s\"  X %.4f  Y %.4f  Z 0.0000",
                    pt.pointLabel, pt.xMeters, pt.yMeters
                )
            )
        }
        sb.appendLine()

        // 8. Objets Lignes & Assignations
        sb.appendLine("\$ LINE OBJECTS")
        for (line in footingLines) {
            sb.appendLine("  LINE \"${line.lineId}\"  PTI \"${line.startPointId}\"  PTJ \"${line.endPointId}\"")
        }
        sb.appendLine()

        sb.appendLine("\$ LINE ASSIGNS")
        for (line in footingLines) {
            val springName = "K_${line.footingSection.sectionName}"
            sb.appendLine("  LINEASSIGN \"${line.lineId}\"  BEAMPROP \"${line.footingSection.sectionName}\"  LINESPRING \"$springName\"")
        }
        sb.appendLine()

        // 9. Charges aux points d'appuis (Point Loads issues d'ETABS)
        sb.appendLine("\$ POINT LOADS")
        for (pt in baseReactions) {
            // DEAD
            sb.appendLine(
                String.format(
                    Locale.US,
                    "  POINTLOAD POINT \"%s\"  LOADCASE \"DEAD\"  FZ %.2f  MX %.2f  MY %.2f",
                    pt.pointLabel, pt.fzDeadKN, pt.mxDeadKNm, pt.myDeadKNm
                )
            )
            // LIVE
            sb.appendLine(
                String.format(
                    Locale.US,
                    "  POINTLOAD POINT \"%s\"  LOADCASE \"LIVE\"  FZ %.2f  MX %.2f  MY %.2f",
                    pt.pointLabel, pt.fzLiveKN, pt.mxLiveKNm, pt.myLiveKNm
                )
            )
            // SEISMIC EX
            if (pt.fzSeismicExKN != 0.0) {
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "  POINTLOAD POINT \"%s\"  LOADCASE \"EX\"  FZ %.2f  FX %.2f",
                        pt.pointLabel, pt.fzSeismicExKN, abs(pt.fzSeismicExKN) * 0.20
                    )
                )
            }
            // SEISMIC EY
            if (pt.fzSeismicEyKN != 0.0) {
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "  POINTLOAD POINT \"%s\"  LOADCASE \"EY\"  FZ %.2f  FY %.2f",
                        pt.pointLabel, pt.fzSeismicEyKN, abs(pt.fzSeismicEyKN) * 0.20
                    )
                )
            }
        }
        sb.appendLine()

        // 10. Combinaisons de charges SAFE
        sb.appendLine("\$ LOAD COMBINATIONS")
        sb.appendLine("  COMBO \"ELU\"  TYPE \"ADD\"")
        sb.appendLine("    CASE \"DEAD\"  SF 1.35")
        sb.appendLine("    CASE \"LIVE\"  SF 1.50")

        sb.appendLine("  COMBO \"ELS\"  TYPE \"ADD\"")
        sb.appendLine("    CASE \"DEAD\"  SF 1.00")
        sb.appendLine("    CASE \"LIVE\"  SF 1.00")

        sb.appendLine("  COMBO \"COMB1\"  TYPE \"ADD\"")
        sb.appendLine("    CASE \"DEAD\"  SF 1.00")
        sb.appendLine("    CASE \"LIVE\"  SF 1.00")
        sb.appendLine("    CASE \"EX\"  SF 1.00")

        sb.appendLine("  COMBO \"COMB3\"  TYPE \"ADD\"")
        sb.appendLine("    CASE \"DEAD\"  SF 1.00")
        sb.appendLine("    CASE \"LIVE\"  SF 1.00")
        sb.appendLine("    CASE \"EY\"  SF 1.00")

        sb.appendLine("  COMBO \"COMB5\"  TYPE \"ADD\"")
        sb.appendLine("    CASE \"DEAD\"  SF 0.80")
        sb.appendLine("    CASE \"EX\"  SF 1.00")

        return sb.toString()
    }

    /**
     * Exporte le modèle SAFE directement dans un fichier cible (.s2k).
     */
    fun exportS2kFile(targetFile: File, report: SafeBridgeReport): File {
        targetFile.bufferedWriter().use { writer ->
            writer.write(report.s2kContent)
        }
        return targetFile
    }
}
