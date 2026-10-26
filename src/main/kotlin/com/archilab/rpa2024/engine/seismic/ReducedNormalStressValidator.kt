package com.archilab.rpa2024.engine.seismic

import kotlin.math.abs

class ReducedNormalStressValidator(
    private val defaultFc28Mpa: Double = 25.0
) {
    /**
     * Contrôle ν = Nd / (Bc * fc28) ≤ 0.35 (RPA 2024 § 7.4.3 - Équation 7.5)
     */
    fun validateColumn(
        story: String,
        columnId: String,
        sectionName: String,
        bCm: Double,
        hCm: Double,
        ndMaxCompressiveKn: Double,
        isSolidToShearWall: Boolean = false,
        fc28Mpa: Double = defaultFc28Mpa
    ): ReducedNormalStressColumnCheck {
        if (isSolidToShearWall) {
            return ReducedNormalStressColumnCheck(
                story = story,
                columnId = columnId,
                sectionName = sectionName,
                bCm = bCm,
                hCm = hCm,
                grossAreaCm2 = bCm * hCm,
                axialForceNdKn = ndMaxCompressiveKn,
                fc28Mpa = fc28Mpa,
                reducedStressNu = 0.0,
                isCompliant = true,
                diagnostic = "Exclu de la vérification ν ≤ 0.35 (poteau solidaire aux voiles - traité selon § 7.7)"
            )
        }

        val grossAreaCm2 = bCm * hCm
        val ndKn = abs(ndMaxCompressiveKn)

        // ν = (Nd [kN] * 10) / (Bc [cm²] * fc28 [MPa])
        val nu = (ndKn * 10.0) / (grossAreaCm2 * fc28Mpa)
        val isCompliant = nu <= 0.35

        val diag = "Section ${bCm.toInt()}x${hCm.toInt()} cm² (Bc=${grossAreaCm2.toInt()} cm²), Nd=${ndKn} kN " +
                "-> ν = ${"%.3f".format(nu)} " +
                if (isCompliant) "≤ 0.35 (Condition Vérifiée)" else "> 0.35 (NON VÉRIFIÉE : Redimensionner le coffrage)"

        return ReducedNormalStressColumnCheck(
            story = story,
            columnId = columnId,
            sectionName = sectionName,
            bCm = bCm,
            hCm = hCm,
            grossAreaCm2 = grossAreaCm2,
            axialForceNdKn = ndKn,
            fc28Mpa = fc28Mpa,
            reducedStressNu = nu,
            isCompliant = isCompliant,
            diagnostic = diag
        )
    }
}
