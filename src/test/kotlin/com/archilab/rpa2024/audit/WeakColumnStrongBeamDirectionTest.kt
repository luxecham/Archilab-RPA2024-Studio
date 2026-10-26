package com.archilab.rpa2024.audit

import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.sample.SampleJointA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Itération 3 : la capacité du poteau doit être évaluée avec la hauteur de section
 * parallèle au moment. Un poteau 30x50 n'a pas la même capacité selon X et Y.
 */
class WeakColumnStrongBeamDirectionTest {

    @Test
    fun `les dimensions de poteau sont orientées selon la direction`() {
        assertEquals(30.0 to 50.0, orientedColumnSection(ColumnShape.RECTANGULAR, 30.0, 50.0, AxisDirection.X))
        assertEquals(50.0 to 30.0, orientedColumnSection(ColumnShape.RECTANGULAR, 30.0, 50.0, AxisDirection.Y))
        assertEquals(40.0 to 40.0, orientedColumnSection(ColumnShape.CIRCULAR, 40.0, 40.0, AxisDirection.X))
    }

    @Test
    fun `le verificateur SCWB produit des capacites finies dans les deux directions`() {
        val imported = SampleJointA.load()
        val assessment = WeakColumnStrongBeamVerifier().assess(imported.model, imported.results)
        assertTrue(assessment.checks.isNotEmpty())
        assertTrue(assessment.checks.all { it.columnMomentKNm.isFinite() && it.beamMomentKNm.isFinite() })
        assertTrue(assessment.checks.any { it.direction == AxisDirection.X })
        assertTrue(assessment.checks.any { it.direction == AxisDirection.Y })
    }

    @Test
    fun `une plus grande hauteur de section augmente la capacité de flexion`() {
        val weak = NodeSectionCapacity.flexuralCapacityKNm(
            breadthCm = 50.0,
            depthCm = 30.0,
            totalSteelMm2 = 2_000.0,
            steelAtCompressionFaceMm2 = 1_000.0,
            steelAtTensionFaceMm2 = 1_000.0,
            coverMeters = 0.05,
            fcdMPa = 16.7,
            fydMPa = 435.0,
            axialKN = 800.0
        )
        val strong = NodeSectionCapacity.flexuralCapacityKNm(
            breadthCm = 30.0,
            depthCm = 50.0,
            totalSteelMm2 = 2_000.0,
            steelAtCompressionFaceMm2 = 1_000.0,
            steelAtTensionFaceMm2 = 1_000.0,
            coverMeters = 0.05,
            fcdMPa = 16.7,
            fydMPa = 435.0,
            axialKN = 800.0
        )
        assertTrue(strong > weak, "Une hauteur de 50 cm doit être plus resistante que 30 cm : $strong / $weak")
    }
}
