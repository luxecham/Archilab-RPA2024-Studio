package com.archilab.rpa2024

import com.archilab.rpa2024.audit.CtcAuditRequest
import com.archilab.rpa2024.audit.CtcComplianceAuditorAndAutoFixer
import com.archilab.rpa2024.audit.CtcFindingStatus
import com.archilab.rpa2024.audit.CtcVerdict
import com.archilab.rpa2024.sample.SampleJointA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Tableau de bord d'audit : exécute les 15 contrôles CTC sur le modèle de référence
 * et vérifie que le verdict reste calculable. Les statuts sont écrits dans la sortie
 * de test pour la revue d'itération.
 */
class AuditDashboardIteration1Test {

    @Test
    fun `les quinze controles CTC sont executes sur le modele de reference`() {
        val imported = SampleJointA.load()
        val report = CtcComplianceAuditorAndAutoFixer().audit(
            CtcAuditRequest(model = imported.model, results = imported.results)
        )
        assertEquals(15, report.controls.size)
        assertNotNull(report.verdict)
        val counts = report.controls.groupingBy { it.status }.eachCount()
        val conformity = report.controls.count { it.status == CtcFindingStatus.CONFORME }
        println(
            "AUDIT_CTC joint=${report.jointName} verdict=${report.verdict.name} " +
                "controls=${report.controls.size} conformes=$conformity " +
                "avertissements=${counts[CtcFindingStatus.AVERTISSEMENT] ?: 0} " +
                "bloquants=${counts[CtcFindingStatus.RESERVE_BLOQUANTE] ?: 0} " +
                "score=${"%.1f".format(java.util.Locale.ROOT, conformity * 100.0 / report.controls.size)}%"
        )
        println(
            "AUDIT_SCWB checks=${report.weakColumnStrongBeam.checks.size} " +
                "failed=${report.weakColumnStrongBeam.failedChecks.size} " +
                "warnings=${report.weakColumnStrongBeam.warningChecks.size}"
        )
        if (report.verdict == CtcVerdict.NON_CONFORME) {
            println("AUDIT_CTC_BLOCKERS=" + report.blockingControls.joinToString(" | ") { it.code + ":" + it.observation })
        }
    }
}
