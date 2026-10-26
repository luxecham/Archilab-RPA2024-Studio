package com.archilab.rpa2024.normative

import com.archilab.rpa2024.model.SiteParameters

/**
 * Classification automatique du site selon le Tableau 3.1 du RPA 2024.
 * Vs30 est prioritaire ; a defaut on utilise Rc30, Pl30, Ep30, N30, qc30 puis Cu30.
 */
object SiteClassifier {

    fun classify(parameters: SiteParameters): SiteClass {
        if (parameters.requiresSpecificStudy) return SiteClass.SS
        if (parameters.softClayThicknessMeters > 3.0) return SiteClass.S4

        parameters.vs30MetersPerSecond?.let { vs30 ->
            return when {
                vs30 > 800.0 -> SiteClass.S1
                vs30 >= 360.0 -> SiteClass.S2
                vs30 >= 180.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        parameters.rc30MPa?.let { rc ->
            return when {
                rc > 10.0 -> SiteClass.S1
                rc >= 0.4 -> SiteClass.S2
                rc >= 0.1 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        parameters.pl30MPa?.let { pl ->
            return when {
                pl > 5.0 -> SiteClass.S1
                pl >= 2.0 -> SiteClass.S2
                pl >= 1.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        parameters.ep30MPa?.let { ep ->
            return when {
                ep > 100.0 -> SiteClass.S1
                ep >= 20.0 -> SiteClass.S2
                ep >= 5.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        parameters.n30?.let { n30 ->
            return when {
                n30 > 50.0 -> SiteClass.S2
                n30 >= 15.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        parameters.qc30MPa?.let { qc ->
            return when {
                qc > 15.0 -> SiteClass.S2
                qc >= 1.5 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        parameters.cu30KPa?.let { cu ->
            return when {
                cu > 100.0 -> SiteClass.S2
                cu >= 50.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        throw NormativeDataException(
            "Classification du site impossible : aucune donnee geotechnique exploitable (Vs30, Rc30, Pl30, Ep30, N30, qc30, Cu30)."
        )
    }
}
