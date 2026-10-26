package com.archilab.rpa2024

import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.BaelSectionInput
import com.archilab.rpa2024.engine.frames.BaelSectionState
import com.archilab.rpa2024.engine.frames.BaelSituation
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamSectionPosition
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.FrameSchematicRenderer
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.parser.BeamForceRow
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.sample.SampleJointA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/**
 * Validation de la partie 3 : noyau BAELR rectangulaire, ferraillage des poteaux
 * (flexion composee) et des poutres (flexion simple), schemas SVG et exigences
 * transversales RPA 2024. Les familles sont parcourues dynamiquement depuis la
 * topologie ETABS du JOINT A : aucune geometrie n'est codee en dur dans les moteurs.
 */
class FrameDesignEngineTest {

    private val imported = SampleJointA.load()
    private val joint = imported.model.joint
    private val results = imported.results
    private val materials = MaterialConfig()
    private val solver = BaelRectangularSolver()

    // ---------------------------------------------------------------------------------
    // Noyau BAELR : flexion simple
    // ---------------------------------------------------------------------------------

    @Test
    fun `flexion simple durable - section simplement armee`() {
        val result = solver.solve(
            BaelSectionInput(
                bCm = 30.0,
                hCm = 50.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                nuKN = 0.0,
                muGKNm = 150.0,
                situation = BaelSituation.DURABLE
            ),
            materials
        )

        assertEquals(BaelSectionState.FLEXION_SIMPLE, result.state)
        assertEquals(14.1667, result.coefficients.fbuMPa, 1e-4)
        assertEquals(434.7826, result.coefficients.fsuMPa, 1e-4)
        assertEquals(0.1743, result.muBu, 5e-4)
        assertEquals(0.3578, result.muLu, 5e-4)
        assertEquals(0.2411, result.alpha, 5e-4)
        assertEquals(10.85, result.neutralAxisMeters * 100.0, 0.02)
        assertEquals(8.49, result.tensionSteelCm2, 0.03)
        assertEquals(0.0, result.compressionSteelCm2, 1e-9)
        assertEquals(1.30, result.minimumSteelCm2, 0.01)
        assertTrue(result.requiredSteelCm2 >= result.minimumSteelCm2 - 1e-9)
    }

    @Test
    fun `flexion simple durable - section doublement armee au-dela de mu lu`() {
        val result = solver.solve(
            BaelSectionInput(
                bCm = 30.0,
                hCm = 50.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                nuKN = 0.0,
                muGKNm = 400.0,
                situation = BaelSituation.DURABLE
            ),
            materials
        )

        assertTrue(result.muBu > result.muLu)
        assertEquals(0.4648, result.muBu, 1e-3)
        assertEquals(0.5833, result.alpha, 1e-3)
        assertEquals(5.29, result.compressionSteelCm2, 0.08)
        assertEquals(25.82, result.tensionSteelCm2, 0.15)
        assertEquals(31.11, result.totalSteelCm2, 0.2)
        assertTrue(result.notes.any { it.contains("doublement armée") })
    }

    @Test
    fun `situation accidentelle - coefficients sismiques`() {
        val durable = solver.coefficients(materials, BaelSituation.DURABLE)
        val accidental = solver.coefficients(materials, BaelSituation.ACCIDENTAL)

        assertEquals(14.1667, durable.fbuMPa, 1e-4)
        assertEquals(434.7826, durable.fsuMPa, 1e-4)
        assertEquals(20.8333, accidental.fbuMPa, 1e-4)
        assertEquals(500.0, accidental.fsuMPa, 1e-9)
        assertTrue(accidental.fbuMPa > durable.fbuMPa)
        assertTrue(accidental.fsuMPa > durable.fsuMPa)

        val elastic = solver.solve(
            BaelSectionInput(bCm = 30.0, hCm = 50.0, muGKNm = 150.0, situation = BaelSituation.DURABLE),
            materials
        )
        val seismic = solver.solve(
            BaelSectionInput(bCm = 30.0, hCm = 50.0, muGKNm = 150.0, situation = BaelSituation.ACCIDENTAL),
            materials
        )
        assertTrue(seismic.tensionSteelCm2 < elastic.tensionSteelCm2)
    }

    // ---------------------------------------------------------------------------------
    // Noyau BAELR : flexion composee
    // ---------------------------------------------------------------------------------

    @Test
    fun `flexion composee - section entierement tendue`() {
        val result = solver.solve(
            BaelSectionInput(
                bCm = 30.0,
                hCm = 50.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                nuKN = -200.0,
                muGKNm = 20.0
            ),
            materials
        )

        assertEquals(BaelSectionState.ENTIEREMENT_TENDUE, result.state)
        assertEquals(-0.10, result.eccentricityMeters, 1e-9)
        assertEquals(0.0, result.neutralAxisMeters, 1e-12)
        assertEquals(3.45, result.topSteelCm2, 0.03)
        assertEquals(1.15, result.bottomSteelCm2, 0.03)
        assertEquals(4.60, result.totalSteelCm2, 0.05)
    }

    @Test
    fun `flexion composee - section partiellement comprimee`() {
        val result = solver.solve(
            BaelSectionInput(
                bCm = 30.0,
                hCm = 50.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                nuKN = 500.0,
                muGKNm = 150.0
            ),
            materials
        )

        assertEquals(BaelSectionState.PARTIELLEMENT_COMPRIMEE, result.state)
        assertEquals(250.0, result.designMomentAtTensionSteelKNm, 1e-6)
        assertEquals(0.2905, result.muBu, 1e-3)
        assertEquals(0.1984, result.neutralAxisMeters, 1e-3)
        assertEquals(4.02, result.tensionSteelCm2, 0.05)
        assertEquals(0.0, result.compressionSteelCm2, 1e-9)
    }

    @Test
    fun `flexion composee - section entierement comprimee en pivot C`() {
        val result = solver.solve(
            BaelSectionInput(
                bCm = 30.0,
                hCm = 50.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                nuKN = 2500.0,
                muGKNm = 50.0
            ),
            materials
        )

        assertEquals(BaelSectionState.ENTIEREMENT_COMPRIMEE, result.state)
        assertEquals(0.50, result.neutralAxisMeters, 1e-9)
        assertTrue(result.notes.any { it.contains("Pivot C") })
        assertEquals(result.topSteelCm2, result.bottomSteelCm2, 1e-9)
        assertTrue(result.totalSteelCm2 >= 20.0)
    }

    // ---------------------------------------------------------------------------------
    // Poteaux : familles dynamiques du JOINT A
    // ---------------------------------------------------------------------------------

    @Test
    fun `chaque famille de poteaux ETABS est ferraillee avec le minimum RPA de la zone III`() {
        val columnEngine = ColumnDesignEngine()
        val set = columnEngine.design(joint, results)

        assertEquals(joint.sectionCatalog.columns.size, set.columns.size)
        assertEquals(3, set.columns.size, "JOINT A : POT35x55, POT40x50 et POT35x35")

        val expectedMinimum = mapOf(
            "POT35x55" to 0.009 * 35.0 * 55.0,
            "POT40x50" to 0.009 * 40.0 * 50.0,
            "POT35x35" to 0.009 * 35.0 * 35.0
        )

        joint.sectionCatalog.columns.forEach { family ->
            val design = set.columns.first { it.family.name == family.name }
            val expected = expectedMinimum.getValue(family.name)
            assertEquals(expected, design.minimumAsCm2, 1e-6, "minimum RPA de ${family.name}")
            assertNotNull(design.layout, "disposition de barres de ${family.name}")
            assertTrue(design.adoptedAsCm2 + 1e-9 >= design.minimumAsCm2, family.name)
            assertTrue(design.adoptedAsCm2 <= design.maximumAsCm2 + 1e-9, family.name)
            assertTrue(design.criticalCases.isNotEmpty(), "efforts ELA de ${family.name}")
            assertTrue(design.checks.any { it.code == "PP10.1_AS_MIN" && it.status == CheckStatus.OK })
            assertTrue(design.checks.none { it.status == CheckStatus.FAILED }, "${family.name} : ${design.checks.filter { it.status == CheckStatus.FAILED }.map { it.code }}")
        }

        assertTrue(set.chapter.lines.any { it.contains("POT35x55") })
        assertEquals(CheckStatus.OK, set.status)
    }

    @Test
    fun `les zones nodales et cadres de poteaux respectent les limites RPA`() {
        val set = ColumnDesignEngine().design(joint, results)

        set.columns.forEach { design ->
            val transverse = design.transverse
            val lclCm = design.family.clearHeightLclMeters * 100.0
            val largestDimension = maxOf(design.sectionWidthCm, design.sectionDepthCm)
            val expectedZone = if (lclCm / largestDimension < 3.0) {
                lclCm
            } else {
                maxOf(1.5 * largestDimension, lclCm / 6.0, 60.0)
            }
            assertEquals(expectedZone, transverse.nodalZoneLengthCm, 1e-6)
            assertTrue(transverse.nodalSpacingCm <= transverse.nodalSpacingLimitCm + 1e-9)
            assertTrue(transverse.currentSpacingCm <= transverse.currentSpacingLimitCm + 1e-9)
            assertTrue(transverse.stirrupAreaCm2 + 1e-9 >= transverse.requiredStirrupAreaCm2)
            assertEquals(50.0 * (design.layout!!.maxDiameterMm / 10.0), transverse.lapLengthCm, 1e-6)
            assertEquals(50.0 * (design.layout!!.maxDiameterMm / 10.0), transverse.anchorageLengthCm, 1e-6)
            transverse.shearChecks.forEach { shear ->
                assertTrue(shear.tauMPa <= shear.tauLimitMPa + 1e-9)
            }
            assertTrue(design.checks.any { it.code == "PP10.2_ESPACEMENT_CADRES" && it.status == CheckStatus.OK })
            assertTrue(design.checks.any { it.code == "PP10.2_ESPACEMENT_CADRES_COURANT" && it.status == CheckStatus.OK })
            assertTrue(design.checks.any { it.code == "PP10.2_ESPACEMENT_FACE" && it.status == CheckStatus.OK })
            assertTrue(design.normalizedAxial <= design.normalizedAxialLimit + 1e-9, design.family.name)
            assertTrue(design.checks.any { it.code == "PP10.3_NU" && it.status == CheckStatus.OK })
            assertTrue(design.checks.any { it.code == "PP10.1_AS_RECOUVREMENT_MAX" && it.status == CheckStatus.OK })
        }
    }

    @Test
    fun `les valeurs chiffrees des articles 7_4 et 7_5 du RPA 2024 sont appliquees`() {
        val columnSet = ColumnDesignEngine().design(joint, results)
        val expectedCriticalZones = mapOf(
            "POT35x55" to 82.5,
            "POT40x50" to 75.0,
            "POT35x35" to 60.0
        )
        expectedCriticalZones.forEach { (name, expected) ->
            val design = columnSet.columns.first { it.family.name == name }
            assertEquals(expected, design.transverse.nodalZoneLengthCm, 1e-9, name)
            assertTrue(
                maxOf(
                    design.layout!!.spacingLongFaceCm,
                    design.layout!!.spacingShortFaceCm
                ) <= 20.0 + 1e-9,
                "$name : espacement de face limité à 20 cm en zone III"
            )
            assertEquals(50.0 * (design.layout!!.maxDiameterMm / 10.0), design.transverse.lapLengthCm, 1e-9)
        }

        val pot40 = columnSet.columns.first { it.family.name == "POT40x50" }
        assertEquals(0.2992, pot40.normalizedAxial, 5e-4)
        assertEquals(0.35, pot40.normalizedAxialLimit, 1e-9)

        val beamSet = BeamDesignEngine().design(joint, beamResults())
        val expectedBeamCriticalZones = mapOf("B30x40" to 60.0, "B30x35" to 52.5)
        expectedBeamCriticalZones.forEach { (name, expected) ->
            val design = beamSet.beams.first { it.family.name == name }
            assertEquals(expected, design.transverse.criticalZoneLengthCm, 1e-9, name)
            assertEquals(2.0 * design.heightCm, design.transverse.nodalZoneLengthCm, 1e-9)
            assertEquals(
                50.0 * (listOfNotNull(
                    design.spanLayout?.maxDiameterMm,
                    design.supportLayout?.maxDiameterMm
                ).maxOrNull()!! / 10.0),
                design.transverse.lapLengthCm,
                1e-9
            )
            assertTrue(
                design.supportLayout!!.continuousAreaCm2 + 1e-9 >= 0.25 * design.topAdoptedAsCm2,
                "$name : quart des chapeaux prolongé"
            )
        }
    }

    // ---------------------------------------------------------------------------------
    // Poutres : familles dynamiques du JOINT A et efforts reconstruits
    // ---------------------------------------------------------------------------------

    private fun beamResults(): EtabsResults {
        val rows = mutableListOf<BeamForceRow>()
        joint.sectionCatalog.beams.forEach { family ->
            val span = family.maxClearSpanMeters.coerceAtLeast(1.0)
            family.elementIds.forEachIndexed { index, element ->
                val story = "N${1 + index % 3}"
                fun add(station: Double, m3: Double, shear: Double, combo: String) {
                    rows += BeamForceRow(
                        story = story,
                        element = element,
                        loadCase = combo,
                        stationMeters = station,
                        pKN = 0.0,
                        v2KN = shear,
                        v3KN = 0.0,
                        m2KNm = 0.0,
                        m3KNm = m3,
                        lengthMeters = span
                    )
                }
                listOf(0.0 to -180.0, span / 2.0 to 120.0, span to -180.0).forEach { (station, moment) ->
                    add(station, moment, 80.0, "ELU_1")
                }
                listOf(0.0 to -130.0, span / 2.0 to 90.0, span to -130.0).forEach { (station, moment) ->
                    add(station, moment, 60.0, "ELS_1")
                }
                listOf(0.0 to -220.0, span / 2.0 to 150.0, span to -220.0).forEach { (station, moment) ->
                    add(station, moment, 100.0, "ELA_1")
                }
            }
        }
        return EtabsResults(beamForces = rows)
    }

    @Test
    fun `chaque famille de poutres ETABS est ferraillee dynamiquement`() {
        val set = BeamDesignEngine().design(joint, beamResults())

        assertEquals(joint.sectionCatalog.beams.size, set.beams.size)
        assertEquals(2, set.beams.size, "JOINT A : B30x40 et B30x35")

        joint.sectionCatalog.beams.forEach { family ->
            val design = set.beams.first { it.family.name == family.name }
            assertEquals(0.005 * family.bCm * family.hCm, design.minimumAsCm2, 1e-6, family.name)
            assertNotNull(design.spanLayout, "barres filantes de ${family.name}")
            assertNotNull(design.supportLayout, "chapeaux de ${family.name}")
            assertTrue(design.bottomAdoptedAsCm2 + 1e-9 >= design.requiredBottomAsCm2)
            assertTrue(design.topAdoptedAsCm2 + 1e-9 >= design.requiredTopAsCm2)
            assertTrue(design.bottomAdoptedAsCm2 + 1e-9 >= 0.5 * design.topAdoptedAsCm2)
            assertTrue(design.bottomAdoptedAsCm2 <= design.maximumAsCm2 + 1e-9)
            assertTrue(design.topAdoptedAsCm2 <= design.maximumAsCm2 + 1e-9)
            assertTrue(design.criticalSections.any { it.position == BeamSectionPosition.SPAN && it.category == ComboCategory.ELU })
            assertTrue(design.criticalSections.any { it.position == BeamSectionPosition.SUPPORT && it.category == ComboCategory.ELA })
            assertTrue(design.checks.any { it.code == "PB10.2_AINF_APPUI" && it.status == CheckStatus.OK })
            assertTrue(design.checks.none { it.status == CheckStatus.FAILED }, "${family.name} : ${design.checks.filter { it.status == CheckStatus.FAILED }.map { it.code }}")
        }

        assertTrue(set.chapter.lines.any { it.contains("B30x40") })
        assertEquals(CheckStatus.OK, set.status)
    }

    @Test
    fun `les quatre verifications de cisaillement de poutre couvrent ELU et ELA`() {
        val set = BeamDesignEngine().design(joint, beamResults())

        set.beams.forEach { design ->
            val categories = design.transverse.shearVerifications.map { it.category }.toSet()
            assertTrue(categories.containsAll(setOf(ComboCategory.ELU, ComboCategory.ELA)))
            design.transverse.shearVerifications.forEach { shear ->
                assertEquals(CheckStatus.OK, shear.status, "${design.family.name} ${shear.category}")
                assertTrue(shear.tauMPa <= shear.tauLimitMPa + 1e-9)
                assertTrue(shear.strutForceKN <= shear.strutLimitKN + 1e-6)
                assertTrue(shear.providedLongitudinalCm2 + 1e-9 >= shear.requiredLongitudinalCm2)
                assertTrue(shear.sigmaBcMPa <= shear.sigmaBcLimitMPa + 1e-9)
            }
            assertTrue(design.checks.any { it.code == "PB10.3_TAU_ELU" })
            assertTrue(design.checks.any { it.code == "PB10.3_BIELLE_ELA" })
            assertTrue(design.checks.any { it.code == "PB10.3_ACIER_LONGITUDINAL_ELU" })
            assertTrue(design.checks.any { it.code == "PB10.3_SIGMA_BC_ELA" })
        }
    }

    @Test
    fun `les zones nodales de poutre appliquent l prime et le premier cadre a cinq centimetres`() {
        val set = BeamDesignEngine().design(joint, beamResults())

        set.beams.forEach { design ->
            val transverse = design.transverse
            assertEquals(2.0 * design.heightCm, transverse.nodalZoneLengthCm, 1e-9)
            assertEquals(1.5 * design.heightCm, transverse.criticalZoneLengthCm, 1e-9)
            assertEquals(5.0, transverse.firstStirrupCm, 1e-9)
            val compressionSteelNeeded = design.criticalSections.any { it.bael.compressionSteelCm2 > 1e-6 }
            val minimumBarDiameterMm = listOfNotNull(
                design.spanLayout?.minDiameterMm,
                design.supportLayout?.minDiameterMm
            ).minOrNull() ?: 12
            val minimumBarDiameterCm = minimumBarDiameterMm / 10.0
            val expectedCurrentLimit = if (compressionSteelNeeded) {
                minOf(design.heightCm / 2.0, design.heightCm / 4.0, 12.0 * minimumBarDiameterCm)
            } else {
                design.heightCm / 2.0
            }
            assertEquals(expectedCurrentLimit, transverse.currentSpacingLimitCm, 1e-9)
            val limit = minOf(
                design.heightCm / 4.0,
                17.5,
                6.0 * minimumBarDiameterCm,
                24.0 * transverse.stirrupDiameterMm / 10.0
            )
            assertEquals(limit, transverse.nodalSpacingLimitCm, 1e-9)
            assertTrue(transverse.nodalSpacingCm <= transverse.nodalSpacingLimitCm + 1e-9)
            assertTrue(transverse.currentSpacingCm <= transverse.currentSpacingLimitCm + 1e-9)
            assertTrue(transverse.stirrupAreaCm2 + 1e-9 >= transverse.requiredStirrupAreaCm2)
            assertEquals(
                0.003 * maxOf(transverse.nodalSpacingCm, transverse.currentSpacingCm) * design.widthCm,
                transverse.requiredStirrupAreaCm2,
                1e-9
            )
            assertTrue(design.checks.any { it.code == "PB10.2_PREMIER_CADRE" })
            assertTrue(design.checks.any { it.code == "PB10.2_ZONE_NODALE" })
            assertTrue(design.checks.any { it.code == "PB10.2_ZONE_CRITIQUE" })
            assertTrue(design.checks.any { it.code == "PB10.2_AT" && it.status == CheckStatus.OK })
            assertTrue(design.checks.any { it.code == "PB10.2_RECOUVREMENT" })
            assertTrue(design.checks.any { it.code == "PB10.2_CHAPEAUX_FILANTS" && it.status == CheckStatus.OK })
            assertTrue(design.checks.any { it.code == "PB10.2_AS_RECOUVREMENT_MAX" && it.status == CheckStatus.OK })
            assertTrue(design.checks.any { it.code == "PB10.2_DIAMETRE" && it.status == CheckStatus.OK })
        }
    }

    @Test
    fun `les exigences de la zone IV sont plus severes qu en zone III`() {
        val columns = ColumnDesignEngine().design(
            joint.sectionCatalog.columns,
            results,
            materials,
            SeismicZone.ZONE_IV
        )
        columns.columns.forEach { design ->
            assertEquals(0.010 * design.family.areaCm2, design.minimumAsCm2, 1e-6, design.family.name)
            assertEquals(60.0 * (design.layout!!.maxDiameterMm / 10.0), design.transverse.lapLengthCm, 1e-9)
            assertTrue(
                maxOf(design.layout!!.spacingLongFaceCm, design.layout!!.spacingShortFaceCm) <= 15.0 + 1e-9,
                "${design.family.name} : espacement de face ≤ 15 cm en zone IV"
            )
            assertTrue(design.transverse.nodalSpacingLimitCm <= 10.0 + 1e-9)
            assertEquals(
                minOf(
                    design.sectionWidthCm / 2.0,
                    design.sectionDepthCm / 2.0,
                    10.0 * (design.layout!!.minDiameterMm / 10.0)
                ),
                design.transverse.currentSpacingLimitCm,
                1e-9,
                design.family.name
            )
            assertTrue(design.checks.any { it.code == "PP10.2_ESPACEMENT_FACE" && it.status == CheckStatus.OK })
            assertTrue(design.checks.none { it.status == CheckStatus.FAILED }, design.family.name)
        }

        val beams = BeamDesignEngine().design(
            joint.sectionCatalog.beams,
            beamResults(),
            materials,
            SeismicZone.ZONE_IV
        )
        beams.beams.forEach { design ->
            val diameters = listOfNotNull(
                design.spanLayout?.minDiameterMm,
                design.supportLayout?.minDiameterMm
            )
            assertTrue(diameters.all { it >= 14 }, "${design.family.name} : φ ≥ 14 mm en zone IV")
            assertEquals(
                60.0 * (listOfNotNull(
                    design.spanLayout?.maxDiameterMm,
                    design.supportLayout?.maxDiameterMm
                ).maxOrNull()!! / 10.0),
                design.transverse.lapLengthCm,
                1e-9
            )
            assertTrue(design.checks.any { it.code == "PB10.2_DIAMETRE" && it.status == CheckStatus.OK })
        }
    }

    // ---------------------------------------------------------------------------------
    // Schemas SVG
    // ---------------------------------------------------------------------------------

    @Test
    fun `le renderer produit des schemas SVG valides pour toutes les familles`(@TempDir tempDir: Path) {
        val renderer = FrameSchematicRenderer()
        val columnSet = ColumnDesignEngine().design(joint, results)
        val beamSet = BeamDesignEngine().design(joint, beamResults())

        columnSet.columns.forEach { design ->
            val svg = renderer.renderColumn(design)
            assertTrue(svg.startsWith("<?xml"), design.family.name)
            assertTrue(svg.contains("<svg"), design.family.name)
            assertTrue(svg.contains("viewBox="), design.family.name)
            assertTrue(svg.contains("id=\"hatch\""), design.family.name)
            assertTrue(svg.contains(">G</text>"), design.family.name)
            assertTrue(svg.contains("Lcr"), design.family.name)
            assertTrue(svg.contains("Lrec"), design.family.name)
            assertTrue(svg.contains("90°"), design.family.name)
            assertTrue(svg.contains("135°"), design.family.name)
            assertTrue(svg.contains(">h = "), design.family.name)
            val path = tempDir.resolve("poteau-${design.family.name}.svg")
            renderer.writeColumnSvg(design, path)
            assertEquals(svg, Files.readString(path))
        }

        beamSet.beams.forEach { design ->
            val svg = renderer.renderBeam(design)
            assertTrue(svg.startsWith("<?xml"), design.family.name)
            assertTrue(svg.contains("<svg"), design.family.name)
            assertTrue(svg.contains("id=\"hatch\""), design.family.name)
            assertTrue(svg.contains("Premier cadre"), design.family.name)
            assertTrue(svg.contains("l' = "), design.family.name)
            assertTrue(svg.contains("Lrec"), design.family.name)
            assertTrue(svg.contains(">Poteau</text>"), design.family.name)
            val path = tempDir.resolve("poutre-${design.family.name}.svg")
            renderer.writeBeamSvg(design, path)
            assertEquals(svg, Files.readString(path))
        }
    }

    @Test
    fun `les efforts poteaux sont bien extraits des tables ETABS`() {
        val design = ColumnDesignEngine().design(joint, results)
        val pot40 = design.columns.first { it.family.name == "POT40x50" }
        val compression = pot40.criticalCases.maxByOrNull { it.axialKN }
        assertNotNull(compression)
        assertEquals(1496.0, compression!!.axialKN, 1e-6)
        assertEquals(ComboCategory.ELA, compression.category)
        assertEquals(BaelSituation.ACCIDENTAL, compression.bael.coefficients.situation)

        val pot35 = design.columns.first { it.family.name == "POT35x35" }
        assertTrue(pot35.criticalCases.any { abs(it.momentKNm - 150.0) < 1e-9 })
        assertFalse(pot40.criticalCases.isEmpty())
    }
}
