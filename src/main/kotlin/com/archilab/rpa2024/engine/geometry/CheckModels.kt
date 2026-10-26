package com.archilab.rpa2024.engine.geometry

import kotlin.math.abs

/** Statut d'une verification reglementaire. */
enum class CheckStatus(val label: String, val severity: Int) {
    OK("Vérifié", 0),
    WARNING("À vérifier", 1),
    FAILED("Non vérifié", 2),
    NOT_APPLICABLE("Non applicable", -1);

    companion object {
        fun worst(statuses: Collection<CheckStatus>): CheckStatus {
            val meaningful = statuses.filter { it != NOT_APPLICABLE }
            if (meaningful.isEmpty()) return NOT_APPLICABLE
            return meaningful.maxBy { it.severity }
        }
    }
}

/** Resultat d'une condition de coffrage ou de verification normative. */
data class DesignCheck(
    val code: String,
    val label: String,
    val reference: String,
    val status: CheckStatus,
    val required: String = "",
    val provided: String = "",
    val message: String = ""
) {
    val isSatisfied: Boolean get() = status == CheckStatus.OK
}

/** Ensemble des verifications d'une famille d'elements (toutes sections identiques). */
data class ElementCheckReport(
    val elementType: String,
    val name: String,
    val geometry: String,
    val checks: List<DesignCheck>
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })
    val failedChecks: List<DesignCheck> get() = checks.filter { it.status == CheckStatus.FAILED }
    val warningChecks: List<DesignCheck> get() = checks.filter { it.status == CheckStatus.WARNING }
}

/** Chapitre de la note de calcul, avec ses lignes de synthese et ses elements detailles. */
data class ChapterReport(
    val number: Int,
    val title: String,
    val summary: String,
    val checks: List<DesignCheck> = emptyList(),
    val elements: List<ElementCheckReport> = emptyList(),
    val lines: List<String> = emptyList()
) {
    val status: CheckStatus
        get() = CheckStatus.worst(checks.map { it.status } + elements.map { it.status })

    val failedCount: Int get() = checks.count { it.status == CheckStatus.FAILED } + elements.count { it.status == CheckStatus.FAILED }
    val warningCount: Int get() = checks.count { it.status == CheckStatus.WARNING } + elements.count { it.status == CheckStatus.WARNING }
}

/** Synthese complete d'un joint importe depuis ETABS. */
data class JointAssessment(
    val jointName: String,
    val chapters: List<ChapterReport>,
    val seismic: SeismicAssessment?,
    val warnings: List<String> = emptyList()
) {
    val overallStatus: CheckStatus
        get() = CheckStatus.worst(chapters.map { it.status })

    val failedCount: Int get() = chapters.sumOf { it.failedCount }
    val warningCount: Int get() = chapters.sumOf { it.warningCount }
    val totalCheckedElements: Int get() = chapters.sumOf { it.elements.size }
}

/** Boite a outils de creation de verifications avec messages en francais. */
object Checks {

    fun format(value: Double, decimals: Int = 2): String =
        String.format(java.util.Locale.ROOT, "%.${decimals}f", value)

    fun min(
        code: String,
        label: String,
        reference: String,
        provided: Double,
        minimum: Double,
        unit: String,
        decimals: Int = 1,
        failureMessage: String = ""
    ): DesignCheck {
        val status = if (provided + 1e-9 >= minimum) CheckStatus.OK else CheckStatus.FAILED
        return DesignCheck(
            code = code,
            label = label,
            reference = reference,
            status = status,
            required = "≥ ${format(minimum, decimals)} $unit",
            provided = "${format(provided, decimals)} $unit",
            message = if (status == CheckStatus.FAILED) {
                failureMessage.ifBlank { "$label insuffisant : ${format(provided, decimals)} $unit < ${format(minimum, decimals)} $unit." }
            } else ""
        )
    }

    fun max(
        code: String,
        label: String,
        reference: String,
        provided: Double,
        maximum: Double,
        unit: String,
        decimals: Int = 1,
        failureMessage: String = "",
        warningAtRatio: Double? = null
    ): DesignCheck {
        val status = when {
            provided - 1e-9 > maximum -> CheckStatus.FAILED
            warningAtRatio != null && provided > maximum * warningAtRatio -> CheckStatus.WARNING
            else -> CheckStatus.OK
        }
        return DesignCheck(
            code = code,
            label = label,
            reference = reference,
            status = status,
            required = "≤ ${format(maximum, decimals)} $unit",
            provided = "${format(provided, decimals)} $unit",
            message = when (status) {
                CheckStatus.FAILED -> failureMessage.ifBlank {
                    "$label dépassé : ${format(provided, decimals)} $unit > ${format(maximum, decimals)} $unit."
                }
                CheckStatus.WARNING -> "$label proche de la limite (${format(provided, decimals)} $unit sur ${format(maximum, decimals)} $unit)."
                else -> ""
            }
        )
    }

    fun between(
        code: String,
        label: String,
        reference: String,
        provided: Double,
        minimum: Double,
        maximum: Double,
        unit: String,
        decimals: Int = 1,
        outOfRangeStatus: CheckStatus = CheckStatus.WARNING,
        message: String = ""
    ): DesignCheck {
        val inside = provided + 1e-9 >= minimum && provided - 1e-9 <= maximum
        return DesignCheck(
            code = code,
            label = label,
            reference = reference,
            status = if (inside) CheckStatus.OK else outOfRangeStatus,
            required = "${format(minimum, decimals)} à ${format(maximum, decimals)} $unit",
            provided = "${format(provided, decimals)} $unit",
            message = if (inside) "" else message.ifBlank {
                "$label hors plage : ${format(provided, decimals)} $unit (attendu ${format(minimum, decimals)} à ${format(maximum, decimals)} $unit)."
            }
        )
    }

    fun boolean(
        code: String,
        label: String,
        reference: String,
        condition: Boolean,
        required: String,
        provided: String,
        failureStatus: CheckStatus = CheckStatus.FAILED,
        message: String = ""
    ): DesignCheck = DesignCheck(
        code = code,
        label = label,
        reference = reference,
        status = if (condition) CheckStatus.OK else failureStatus,
        required = required,
        provided = provided,
        message = if (condition) "" else message
    )

    fun info(code: String, label: String, reference: String, value: String): DesignCheck =
        DesignCheck(code, label, reference, CheckStatus.NOT_APPLICABLE, provided = value)

    fun notApplicable(code: String, label: String, reference: String, message: String): DesignCheck =
        DesignCheck(code, label, reference, CheckStatus.NOT_APPLICABLE, message = message)

    fun isClose(a: Double, b: Double, tolerance: Double = 1e-6): Boolean = abs(a - b) <= tolerance
}
