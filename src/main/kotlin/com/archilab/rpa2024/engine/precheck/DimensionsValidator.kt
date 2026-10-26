package com.archilab.rpa2024.engine.precheck

import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.SeismicZone
import kotlin.math.*

data class ColumnCheckReport(
    val columnId: String,
    val sectionStr: String,
    val isMinDimValid: Boolean,
    val isSlendernessValid: Boolean,
    val isAspectRatioValid: Boolean,
    val isOverallValid: Boolean,
    val diagnostic: String
)

data class BeamCheckReport(
    val beamId: String,
    val sectionStr: String,
    val isWidthValid: Boolean,
    val isHeightValid: Boolean,
    val isAspectRatioValid: Boolean,
    val isOverhangValid: Boolean,
    val isOverallValid: Boolean,
    val diagnostic: String
)

class DimensionsValidator(
    private val zone: SeismicZone = SeismicZone.ZONE_III
) {
    /**
     * Vérification stricte des conditions de coffrage des poteaux (RPA 2024 § 7.4.1 - Figure 7.1)
     */
    fun validateColumn(col: ColumnElement): ColumnCheckReport {
        val minDim = min(col.bCm, col.hCm)
        val lclCm = col.clearHeightMeters * 100.0

        // 1. Min(b1, h1) >= 25 cm (Zones I, II, III) ou 30 cm (Zones IV, V, VI)
        val check1 = minDim >= zone.minColDimCm

        // 2. Min(b1, h1) >= lcl / 20
        val check2 = minDim >= (lclCm / 20.0)

        // 3. 1/4 < b1 / h1 < 4
        val ratio = col.bCm / col.hCm
        val check3 = ratio > 0.25 && ratio < 4.0

        val passed = check1 && check2 && check3
        val diag = "Poteau ${col.bCm.toInt()}x${col.hCm.toInt()} cm (lcl=${"%.2f".format(col.clearHeightMeters)}m) : " +
                "MinDim >= ${zone.minColDimCm.toInt()}cm [$check1], " +
                "lcl/20 = ${(lclCm / 20.0).roundToInt()}cm [$check2], " +
                "Ratio b/h = ${"%.2f".format(ratio)} [$check3] -> " +
                if (passed) "Condition Vérifiée (CV)" else "NON CONFORME RPA"

        return ColumnCheckReport(col.id, "${col.bCm.toInt()}x${col.hCm.toInt()}", check1, check2, check3, passed, diag)
    }

    /**
     * Vérification stricte des conditions de coffrage des poutres (RPA 2024 § 7.5.1 - Figure 7.4)
     */
    fun validateBeam(beam: BeamElement, colSupportDimCm: Double = 50.0): BeamCheckReport {
        val minWidthReq = if (zone in listOf(SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI)) 25.0 else 20.0

        // 1. b >= 20 cm (ou 25 cm)
        val check1 = beam.bCm >= minWidthReq

        // 2. h >= 30 cm
        val check2 = beam.hCm >= 30.0

        // 3. h / b <= 4.0
        val ratio = beam.hCm / beam.bCm
        val check3 = ratio <= 4.0

        // 4. b_max <= 1.5h + b1 & |b - b1| <= max(b1/2, h1/2)
        val bMax = 1.5 * beam.hCm + colSupportDimCm
        val check4 = beam.bCm <= bMax

        val passed = check1 && check2 && check3 && check4
        val diag = "Poutre ${beam.bCm.toInt()}x${beam.hCm.toInt()} cm : " +
                "b >= ${minWidthReq.toInt()}cm [$check1], " +
                "h >= 30cm [$check2], " +
                "h/b = ${"%.2f".format(ratio)} <= 4.0 [$check3], " +
                "b_max <= ${bMax.toInt()}cm [$check4] -> " +
                if (passed) "Condition Vérifiée (CV)" else "NON CONFORME RPA"

        return BeamCheckReport(beam.id, "${beam.bCm.toInt()}x${beam.hCm.toInt()}", check1, check2, check3, check4, passed, diag)
    }
}
