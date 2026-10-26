package com.archilab.rpa2024.model

import com.archilab.rpa2024.normative.BracingSystem
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.normative.SiteClass
import kotlinx.serialization.Serializable

/**
 * Projet global pouvant comporter 1 à N joints ou blocs structuraux indépendants.
 */
@Serializable
data class MasterProject(
    val projectTitle: String,
    val wilaya: String,
    val joints: List<StructuralJoint> = emptyList()
) {
    fun findJoint(name: String): StructuralJoint? =
        joints.find { it.jointName.equals(name, ignoreCase = true) }
}

/**
 * Sous-structure indépendante (Joint / Bloc) séparée par des joints sismiques.
 * Entièrement autonome, correspondant à un modèle de calcul ETABS.
 */
@Serializable
data class StructuralJoint(
    val jointName: String,
    val gridSystem: GridSystem,
    val storySystem: StorySystem,
    val sectionCatalog: SectionCatalog,
    val materials: MaterialConfig = MaterialConfig(),
    val seismicParams: SeismicConfig = SeismicConfig()
)

/**
 * Configuration des matériaux pour la structure selon le CBA 93 et le BAEL 91 révisé 99.
 */
@Serializable
data class MaterialConfig(
    val concreteGrade: String = "C25/30",
    val fc28MPa: Double = 25.0,
    val steelGrade: String = "FeE500",
    val feMPa: Double = 500.0,
    val meshFeMPa: Double = 215.0,
    val soilBearingCapacityBars: Double = 1.20,
    val foundationEmbedmentMeters: Double = 1.50,
    val soilSubgradeModulusKNm3: Double = 21848.0
)

/**
 * Paramètres sismiques réglementaires du Joint selon le RPA 2024.
 */
@Serializable
data class SeismicConfig(
    val zone: SeismicZone = SeismicZone.ZONE_III,
    val site: SiteClass = SiteClass.S3,
    val importanceGroup: ImportanceGroup = ImportanceGroup.GROUP_1A,
    val bracingSystem: BracingSystem = BracingSystem.CAT_3,
    val dampingRatioXiPercent: Double = 7.0,
    val liveLoadCategory: LiveLoadCategory = LiveLoadCategory.HABITATION_BUREAUX,
    val planRegular: Boolean = true,
    val elevationRegular: Boolean = true,
    val redundantWalls: Boolean = false,
    val useCustomNuA: Boolean = false,
    val customNuA: Double = 0.50
)
