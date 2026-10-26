package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable

@Serializable
data class MasterProject(
    val projectTitle: String,
    val wilaya: String,
    val joints: List<StructuralJoint> = emptyList()
) {
    fun joint(name: String): StructuralJoint? =
        joints.firstOrNull { it.jointName.equals(name.trim(), ignoreCase = true) }

    val jointCount: Int get() = joints.size

    val totalElementCount: Int get() = joints.sumOf { it.sectionCatalog.totalElementCount }
}

@Serializable
data class StructuralJoint(
    val jointName: String,
    val gridSystem: GridSystem,
    val storySystem: StorySystem,
    val sectionCatalog: SectionCatalog,
    val materials: MaterialConfig,
    val seismicParams: SeismicConfig
) {
    val elementCount: Int get() = sectionCatalog.totalElementCount
}
