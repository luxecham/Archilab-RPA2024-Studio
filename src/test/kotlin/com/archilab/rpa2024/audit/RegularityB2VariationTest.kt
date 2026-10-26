package com.archilab.rpa2024.audit

import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.parser.DiaphragmDisplacementRow
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.MassRigidityCenterRow
import com.archilab.rpa2024.parser.StoryShearRow
import com.archilab.rpa2024.sample.SampleJointA
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Itération 2 : le critère b2 doit détecter toute variation excessive de masse ou de
 * raideur entre niveaux consécutifs, y compris une augmentation brutale.
 */
class RegularityB2VariationTest {

    @Test
    fun `une augmentation de masse de plus de trente pour cent rend l elevation irreguliere`() {
        val imported = SampleJointA.load()
        val stories = imported.model.joint.storySystem.mainStories
        val floors = stories.map {
            FloorGeometry.rectangle(it.storyName, 0.0, 15.0, 0.0, 20.65)
        }
        val centers = stories.mapIndexed { index, story ->
            val mass = if (index == stories.lastIndex) 2000.0 else 1000.0
            MassRigidityCenterRow(
                story = story.storyName,
                diaphragm = "D1",
                massX = mass,
                massY = mass,
                xcmMeters = 7.5,
                ycmMeters = 10.325,
                xcrMeters = 7.5,
                ycrMeters = 10.325
            )
        }
        val results = EtabsResults(
            storyShears = stories.flatMap { story ->
                listOf(
                    StoryShearRow(story.storyName, "Bottom", "EX", 1000.0, 0.0),
                    StoryShearRow(story.storyName, "Bottom", "EY", 0.0, 1000.0)
                )
            },
            diaphragmDisplacements = stories.mapIndexed { index, story ->
                val value = 0.002 * (index + 1)
                DiaphragmDisplacementRow(story.storyName, "EX", value, 0.0)
            }
        )
        val assessment = RegularityAnalyzerRpa2024().analyze(
            RegularityAnalysisRequest(
                model = imported.model,
                results = results,
                floors = floors,
                centers = centers,
                options = RegularityCriteriaOptions(
                    includePenthouse = false,
                    maximumMassVariation = 0.30,
                    maximumStiffnessVariation = 0.90
                )
            )
        )
        val b2 = assessment.elevationCriteria.first { it.code == "b2" }
        // Le statut peut rester WARNING si les raideurs ne sont pas toutes disponibles ;
        // l'important est que le critère ne soit plus déclaré satisfait.
        assertFalse(b2.satisfied, b2.observed)
        assertTrue(b2.observed.contains("augmentation"), b2.observed)
    }
}
