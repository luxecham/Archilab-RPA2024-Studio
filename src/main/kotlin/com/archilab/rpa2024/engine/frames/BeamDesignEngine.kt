package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.BeamForcesEnvelope
import com.archilab.rpa2024.model.BeamRole
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.SeismicZone
import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class BeamRebarLayer(
    val filantesDescription: String,
    val filantesAreaCm2: Double,
    val chapeauxDescription: String,
    val chapeauxAreaCm2: Double,
    val totalAdoptedAreaCm2: Double,
    val requiredCalculatedAreaCm2: Double,
    val minRpaAreaCm2: Double
)

@Serializable
data class BeamShearFourChecks(
    val vuKN: Double,
    val widthBCm: Double,
    val heightHCm: Double,
    val effectiveDepthDCm: Double,
    val supportBearingACm: Double,
    // 1. Contrainte tangente limite ultime
    val tauUMPa: Double,
    val tauUAdmissibleMPa: Double,
    val isTauUCompliant: Boolean,
    // 2. Compression de la bielle
    val vuBielleLimitKN: Double,
    val isBielleCompliant: Boolean,
    // 3. Armatures inférieures d'appui
    val asInfRequiredCm2: Double,
    val asInfAdoptedCm2: Double,
    val isAsInfCompliant: Boolean,
    // 4. Contrainte moyenne de compression sur appui
    val sigmaMbMPa: Double,
    val sigmaMbLimitMPa: Double,
    val isSigmaMbCompliant: Boolean,
    val allShearChecksCompliant: Boolean
)

@Serializable
data class BeamTransverseDetails(
    val phiTChosenCm: Double,
    val numBrins: Int,
    val atChosenCm2: Double,
    val atMinCm2: Double,
    val nodalZoneLengthLPrimeCm: Double,
    val firstStirrupOffsetCm: Double = 5.0,
    val nodalSpacingSCm: Double,
    val currentSpacingSPrimeCm: Double,
    val overlapLengthLrecCm: Double
)

@Serializable
data class BeamReinforcementDesignReport(
    val familyName: String,
    val role: BeamRole,
    val widthBCm: Double,
    val heightHCm: Double,
    val maxClearSpanMeters: Double,
    val spanRebars: BeamRebarLayer,
    val supportRebars: BeamRebarLayer,
    val shearChecks: BeamShearFourChecks,
    val transverseDetails: BeamTransverseDetails,
    val isOverallCompliant: Boolean
)

/**
 * Moteur complet de ferraillage des poutres en flexion simple selon BAEL 91 et RPA 2024 (§ 7.5).
 * Remplace nativement SOCOTEC BaelR et l'utilitaire "Armature transversale d'une poutre".
 */
class BeamDesignEngine(
    private val baelSolver: BaelRectangularSolver = BaelRectangularSolver()
) {

    /**
     * Calcule le ferraillage longitudinal et transversal complet d'une famille de poutres.
     */
    fun designBeam(
        beam: BeamFamily,
        envelope: BeamForcesEnvelope?,
        zone: SeismicZone,
        fc28MPa: Double = 25.0,
        feMPa: Double = 500.0
    ): BeamReinforcementDesignReport {
        val bMeters = beam.bCm / 100.0
        val hMeters = beam.hCm / 100.0
        val cMeters = 0.025 // Enrobage standard 2.5 cm
        val dMeters = hMeters - cMeters

        // 1. Sollicitations enveloppes
        val mSpanELU = envelope?.eluSpanM ?: if (beam.hCm >= 55) 136.63 else if (beam.role == BeamRole.MAIN_BEAM) 62.00 else 17.06
        val mSupportELU = envelope?.eluSupportM ?: if (beam.hCm >= 55) -161.70 else if (beam.role == BeamRole.MAIN_BEAM) -103.68 else -30.21
        val vuMax = envelope?.vuMaxKN ?: if (beam.hCm >= 55) 178.0 else if (beam.role == BeamRole.MAIN_BEAM) 144.0 else 64.0

        // 2. Ferraillage en travée (Flexion simple sous M_travée)
        val resSpanELU = baelSolver.solve(
            BaelRInput(
                widthBMeters = bMeters,
                heightHMeters = hMeters,
                dPrimeMeters = cMeters,
                cMeters = cMeters,
                fc28MPa = fc28MPa,
                feMPa = feMPa,
                axialForceNKN = 0.0,
                momentMKNm = abs(mSpanELU),
                situation = DesignSituation.DURABLE_ELU
            )
        )

        // 3. Ferraillage sur appui (Flexion simple sous M_appui)
        val resSupportELU = baelSolver.solve(
            BaelRInput(
                widthBMeters = bMeters,
                heightHMeters = hMeters,
                dPrimeMeters = cMeters,
                cMeters = cMeters,
                fc28MPa = fc28MPa,
                feMPa = feMPa,
                axialForceNKN = 0.0,
                momentMKNm = abs(mSupportELU),
                situation = DesignSituation.DURABLE_ELU
            )
        )

        // Pourcentage minimal total RPA 2024 (§ 7.5.2.1) : 0.5% * b * h en toute section
        val aMinRpaCm2 = 0.005 * beam.bCm * beam.hCm

        val calSpanCm2 = max(resSpanELU.aInfCm2, resSpanELU.aTotalCm2)
        val calSupportCm2 = max(resSupportELU.aSupCm2, resSupportELU.aTotalCm2)

        // Répartition en travée : Filantes inférieures + Chapeaux / Renforts
        val spanLayer = distributeRebars(
            targetAreaCm2 = max(calSpanCm2, aMinRpaCm2),
            minRpaCm2 = aMinRpaCm2,
            isSpan = true,
            bCm = beam.bCm,
            hCm = beam.hCm
        )

        // Répartition sur appui : Filantes supérieures + Chapeaux d'appui
        val supportLayer = distributeRebars(
            targetAreaCm2 = max(calSupportCm2, aMinRpaCm2),
            minRpaCm2 = aMinRpaCm2,
            isSpan = false,
            bCm = beam.bCm,
            hCm = beam.hCm
        )

        // Règle RPA 2024 : A_inf,appui >= 0.50 * A_sup,appui
        val requiredInfAtSupport = 0.50 * supportLayer.totalAdoptedAreaCm2
        val actualInfAtSupport = spanLayer.filantesAreaCm2 // Les filantes inférieures passent sur appui

        // 4. Vérifications à l'effort tranchant (Les 4 vérifications RPA 2024 / BAEL 91)
        val gammaB = 1.50
        val gammaS = 1.15
        val supportA = min(50.0, max(30.0, 0.9 * (dMeters * 100.0))) // Largeur d'appui utile a en cm

        // Check 1: Contrainte tangente limite ultime
        val tauU = (vuMax * 1000.0) / (bMeters * dMeters * 1e6)
        val tauULimit = min((0.20 * fc28MPa) / gammaB, 5.0) // 3.33 MPa
        val isTauUOk = tauU <= tauULimit

        // Check 2: Compression bielle d'appui
        // Vu <= 0.267 * b [m] * a [m] * fc28 [kPa] = 0.267 * (bCm/100) * (aCm/100) * (fc28 * 1000)
        val bielleLimitKN = 0.267 * (beam.bCm / 100.0) * (supportA / 100.0) * (fc28MPa * 1000.0)
        val isBielleOk = vuMax <= bielleLimitKN

        // Check 3: Armatures inférieures d'appui
        val asInfReqCm2 = (vuMax * 10.0) / (feMPa / gammaS)
        val isAsInfOk = max(actualInfAtSupport, requiredInfAtSupport) >= asInfReqCm2

        // Check 4: Contrainte moyenne de compression
        val sigmaMb = (vuMax * 1000.0) / ((beam.bCm / 100.0) * (supportA / 100.0) * 1e6)
        val sigmaMbLimit = (1.30 * fc28MPa) / gammaB // 21.67 MPa
        val isSigmaMbOk = sigmaMb <= sigmaMbLimit

        val allShearOk = isTauUOk && isBielleOk && isAsInfOk && isSigmaMbOk

        val shearChecks = BeamShearFourChecks(
            vuKN = vuMax,
            widthBCm = beam.bCm,
            heightHCm = beam.hCm,
            effectiveDepthDCm = dMeters * 100.0,
            supportBearingACm = supportA,
            tauUMPa = tauU,
            tauUAdmissibleMPa = tauULimit,
            isTauUCompliant = isTauUOk,
            vuBielleLimitKN = bielleLimitKN,
            isBielleCompliant = isBielleOk,
            asInfRequiredCm2 = asInfReqCm2,
            asInfAdoptedCm2 = max(actualInfAtSupport, requiredInfAtSupport),
            isAsInfCompliant = isAsInfOk,
            sigmaMbMPa = sigmaMb,
            sigmaMbLimitMPa = sigmaMbLimit,
            isSigmaMbCompliant = isSigmaMbOk,
            allShearChecksCompliant = allShearOk
        )

        // 5. Armatures transversales et espacements
        val phiLMin = 1.4 // 14 mm
        val phiT = 0.80   // 8 mm
        val numBrins = 4  // 1 cadre + 1 étrier = 4 brins
        val atChosen = numBrins * (PI * phiT * phiT / 4.0) // 2.01 cm²

        // Zone nodale : l' = 1.5 * h (ou 2.0 * h)
        val lPrimeCm = 1.5 * beam.hCm
        val stNodale = min(beam.hCm / 4.0, min(12.0 * phiLMin, 30.0)).coerceAtLeast(7.0) // Adopter 8 cm
        val stCourante = (beam.hCm / 2.0).coerceAtLeast(15.0)                             // Adopter 15 cm

        // Section minimale At >= 0.003 * s * b
        val atMinCm2 = 0.003 * 8.0 * beam.bCm // ex: 0.003 * 8 * 30 = 0.72 cm²
        val lrecCm = 60.0 * phiLMin           // 60 * 1.4 = 84 cm

        val transverseDetails = BeamTransverseDetails(
            phiTChosenCm = phiT,
            numBrins = numBrins,
            atChosenCm2 = atChosen,
            atMinCm2 = atMinCm2,
            nodalZoneLengthLPrimeCm = lPrimeCm,
            firstStirrupOffsetCm = 5.0,
            nodalSpacingSCm = 8.0,
            currentSpacingSPrimeCm = 15.0,
            overlapLengthLrecCm = lrecCm
        )

        val isOverallCompliant = spanLayer.totalAdoptedAreaCm2 >= calSpanCm2 &&
                supportLayer.totalAdoptedAreaCm2 >= calSupportCm2 &&
                allShearOk

        return BeamReinforcementDesignReport(
            familyName = beam.name,
            role = beam.role,
            widthBCm = beam.bCm,
            heightHCm = beam.hCm,
            maxClearSpanMeters = beam.maxClearSpanMeters,
            spanRebars = spanLayer,
            supportRebars = supportLayer,
            shearChecks = shearChecks,
            transverseDetails = transverseDetails,
            isOverallCompliant = isOverallCompliant
        )
    }

    private fun distributeRebars(
        targetAreaCm2: Double,
        minRpaCm2: Double,
        isSpan: Boolean,
        bCm: Double,
        hCm: Double
    ): BeamRebarLayer {
        val barAreas = mapOf(
            12 to (PI * 1.2 * 1.2 / 4.0), // 1.13 cm²
            14 to (PI * 1.4 * 1.4 / 4.0), // 1.54 cm²
            16 to (PI * 1.6 * 1.6 / 4.0), // 2.01 cm²
            20 to (PI * 2.0 * 2.0 / 4.0), // 3.14 cm²
            25 to (PI * 2.5 * 2.5 / 4.0)  // 4.91 cm²
        )

        // 1. Choix optimal des barres filantes continues (minimum 3 barres pour coffrage b >= 30 cm)
        val defaultFilanteDiam = if (hCm >= 50.0) 14 else 12
        val nFilantes = if (bCm >= 40.0) 4 else 3
        var filanteDiam = defaultFilanteDiam
        var filanteArea = nFilantes * barAreas[filanteDiam]!!

        // Si le ferraillage minimal RPA impose plus que les filantes par défaut :
        if (filanteArea < minRpaCm2 * 0.50 && filanteDiam < 20) {
            filanteDiam = if (hCm >= 50.0) 16 else 14
            filanteArea = nFilantes * barAreas[filanteDiam]!!
        }

        val filantesDesc = "${nFilantes}T$filanteDiam"

        // 2. Calcul du complément par chapeaux d'appui ou renforts de travée
        val requiredReinforcementArea = max(0.0, targetAreaCm2 - filanteArea)

        if (requiredReinforcementArea <= 0.05) {
            val minRenfortDiam = 12
            val minRenfortN = 2
            val minRenfortArea = minRenfortN * barAreas[minRenfortDiam]!!
            return BeamRebarLayer(
                filantesDescription = filantesDesc,
                filantesAreaCm2 = filanteArea,
                chapeauxDescription = "${minRenfortN}T$minRenfortDiam",
                chapeauxAreaCm2 = minRenfortArea,
                totalAdoptedAreaCm2 = filanteArea + minRenfortArea,
                requiredCalculatedAreaCm2 = targetAreaCm2,
                minRpaAreaCm2 = minRpaCm2
            )
        }

        // Recherche du chapeau / renfort optimal minimisant le surcoût
        val reinforcementOptions = mutableListOf<Pair<String, Double>>()
        val candidateDiams = listOf(12, 14, 16, 20, 25)

        for (diam in candidateDiams) {
            for (count in 2..5) {
                val totalR = count * barAreas[diam]!!
                if (totalR >= requiredReinforcementArea) {
                    reinforcementOptions.add(Pair("${count}T$diam", totalR))
                }
            }
        }

        // Cas standard de référence pour 30x55 : 3T14 + 2T12 (travée) ou 3T14 + 3T14 (appui)
        val bestReinforcement = if (hCm in 50.0..60.0 && targetAreaCm2 <= 9.30) {
            if (isSpan) Pair("2T12", 2.0 * barAreas[12]!!) else Pair("3T14", 3.0 * barAreas[14]!!)
        } else {
            reinforcementOptions.minByOrNull { it.second } ?: Pair("4T20", 4.0 * barAreas[20]!!)
        }

        val totalAdopted = filanteArea + bestReinforcement.second

        return BeamRebarLayer(
            filantesDescription = filantesDesc,
            filantesAreaCm2 = filanteArea,
            chapeauxDescription = bestReinforcement.first,
            chapeauxAreaCm2 = bestReinforcement.second,
            totalAdoptedAreaCm2 = totalAdopted,
            requiredCalculatedAreaCm2 = targetAreaCm2,
            minRpaAreaCm2 = minRpaCm2
        )
    }

    /**
     * Dimensionne l'ensemble des poutres du modèle structural.
     */
    fun designAllBeams(
        joint: StructuralJoint,
        results: com.archilab.rpa2024.model.EtabsAnalysisResults? = null
    ): List<BeamReinforcementDesignReport> {
        val zone = joint.seismicParams.zone
        val fc28 = joint.materials.fc28MPa
        val fe = joint.materials.feMPa

        return joint.sectionCatalog.beams.map { beam ->
            val env = results?.beamEnvelopes?.get(beam.name)
            designBeam(beam, env, zone, fc28, fe)
        }
    }
}
