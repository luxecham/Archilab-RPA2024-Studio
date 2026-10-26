package com.archilab.rpa2024.engine.geometry

import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.WallSlenderness
import kotlinx.serialization.Serializable

@Serializable
enum class CheckStatus {
    PASSED,
    WARNING,
    FAILED
}

@Serializable
data class CheckItem(
    val code: String,
    val description: String,
    val actualValue: Double,
    val limitValue: Double,
    val comparisonOp: String, // "<=", ">=", "in"
    val status: CheckStatus,
    val message: String
)

@Serializable
data class BeamCheckResult(
    val beamFamilyName: String,
    val bCm: Double,
    val hCm: Double,
    val clearSpanMeters: Double,
    val rigidityHeightCheck: CheckItem,
    val rigidityWidthCheck: CheckItem,
    val minWidthCheck: CheckItem,
    val minHeightCheck: CheckItem,
    val slendernessCheck: CheckItem,
    val overhangCheck: CheckItem,
    val eccentricityCheck: CheckItem,
    val isCompliant: Boolean
)

@Serializable
data class ColumnCheckResult(
    val columnFamilyName: String,
    val shape: ColumnShape,
    val b1Cm: Double,
    val h1Cm: Double,
    val clearHeightLclMeters: Double,
    val minDimensionCheck: CheckItem,
    val clearHeightRatioCheck: CheckItem,
    val aspectRatioCheck: CheckItem,
    val isCompliant: Boolean
)

@Serializable
data class ShearWallCheckResult(
    val pierName: String,
    val lengthLwMeters: Double,
    val thicknessBwMeters: Double,
    val clearHeightHeMeters: Double,
    val totalHeightHwMeters: Double,
    val lengthQualificationCheck: CheckItem,
    val thicknessCheck: CheckItem,
    val isWallQualified: Boolean,
    val slenderness: WallSlenderness,
    val criticalZoneHeightHcrMeters: Double,
    val isCompliant: Boolean
)

@Serializable
data class ReducedNormalForceCheckResult(
    val elementType: String, // "COLUMN" ou "SHEAR_WALL"
    val elementName: String,
    val sectionAreaCm2: Double,
    val ndKN: Double,
    val fc28MPa: Double,
    val nuComputed: Double,
    val nuLimit: Double,
    val isWallBoundaryExcluded: Boolean = false,
    val isCompliant: Boolean
)

@Serializable
data class SlabCheckResult(
    val slabName: String,
    val htCm: Double,
    val maxSpanMeters: Double,
    val minRequiredHtCm: Double,
    val isCompliant: Boolean
)

@Serializable
data class DriftCheckResult(
    val story: String,
    val direction: AxisDirection,
    val deltaEkMeters: Double,
    val deltaKMeters: Double,
    val relativeDriftDeltaKMeters: Double,
    val nuADeltaKMeters: Double,
    val limitDeltaBarKMeters: Double,
    val isCompliant: Boolean
)

@Serializable
data class PDeltaCheckResult(
    val story: String,
    val direction: AxisDirection,
    val hkMeters: Double,
    val pkKN: Double,
    val deltaKMeters: Double,
    val vkKN: Double,
    val thetaK: Double,
    val limitTheta: Double = 0.10,
    val isCompliant: Boolean
)

@Serializable
data class LoadCombination(
    val name: String,
    val description: String,
    val formula: String,
    val isSeismic: Boolean
)

@Serializable
data class GlobalJointCheckReport(
    val jointName: String,
    val mechanicalProps: MechanicalProperties,
    val beamChecks: List<BeamCheckResult>,
    val columnChecks: List<ColumnCheckResult>,
    val shearWallChecks: List<ShearWallCheckResult>,
    val reducedNormalForceChecks: List<ReducedNormalForceCheckResult>,
    val slabChecks: List<SlabCheckResult>,
    val driftChecks: List<DriftCheckResult>,
    val pDeltaChecks: List<PDeltaCheckResult>,
    val loadCombinations: List<LoadCombination>,
    val allChecksCompliant: Boolean
)
