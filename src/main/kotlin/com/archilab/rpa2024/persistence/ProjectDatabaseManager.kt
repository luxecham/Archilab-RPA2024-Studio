package com.archilab.rpa2024.persistence

import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.BeamRole
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.GridLine
import com.archilab.rpa2024.model.GridSystem
import com.archilab.rpa2024.model.MasterProject
import com.archilab.rpa2024.model.SectionCatalog
import com.archilab.rpa2024.model.SeismicConfig
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.SlabStripTopology
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

enum class RebarElementType(val label: String) {
    COLUMN("Poteau"),
    BEAM("Poutre"),
    WALL("Voile"),
    SLAB("Plancher"),
    FOUNDATION("Fondation"),
    SECONDARY("Élément secondaire")
}

/**
 * Modification manuelle de ferraillage saisie dans l'interface. Elle est conservée
 * séparément du résultat calculé afin qu'une nouvelle analyse ne l'écrase pas.
 */
data class ManualRebarOverride(
    val elementType: RebarElementType,
    val familyName: String,
    val adoptedRebar: String,
    val secondaryRebar: String? = null,
    val axialKN: Double? = null,
    val utilization: Double? = null,
    val shearStressMPa: Double? = null,
    val notes: String = ""
) {
    init {
        require(familyName.isNotBlank()) { "La famille de ferraillage doit être nommée." }
        require(adoptedRebar.isNotBlank()) { "La désignation d'acier adoptée est obligatoire." }
    }
}

data class CalculationRecord(
    val id: Long? = null,
    val jointId: Long? = null,
    val chapterId: Int,
    val title: String = "Calcul",
    val jsonPayload: String,
    val createdAt: Instant = Instant.now()
)

data class JointArchive(
    val joint: StructuralJoint,
    val jointId: Long? = null,
    val loads: Map<String, String> = emptyMap(),
    val manualRebars: List<ManualRebarOverride> = emptyList(),
    val calculations: List<CalculationRecord> = emptyList()
)

data class StoredProject(
    val id: Long? = null,
    val title: String,
    val wilaya: String,
    val engineerName: String = "",
    val clientName: String = "",
    val reference: String = "",
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val joints: List<JointArchive> = emptyList()
)

data class ProjectSummary(
    val id: Long,
    val title: String,
    val wilaya: String,
    val engineerName: String,
    val updatedAt: Instant?,
    val jointCount: Int
)

// ---------------------------------------------------------------------------- schema

object Projects : LongIdTable("Projects") {
    val title = varchar("title", 255)
    val wilaya = varchar("wilaya", 120)
    val engineerName = varchar("engineer_name", 255).default("")
    val clientName = varchar("client_name", 255).default("")
    val reference = varchar("reference", 160).default("")
    val createdAt = varchar("created_at", 40)
    val updatedAt = varchar("updated_at", 40)
}

object StructuralJoints : LongIdTable("StructuralJoints") {
    val projectId = reference("project_id", Projects, onDelete = ReferenceOption.CASCADE)
    val jointName = varchar("joint_name", 160)
    val seismicZone = varchar("seismic_zone", 16)
    val siteClass = varchar("site_class", 16)
    val importanceGroup = varchar("importance_group", 16)
    val bracingSystem = integer("bracing_system")
    val bracingSystemLabel = varchar("bracing_system_label", 255).default("")
    val rCoeff = double("r_coeff")
    val qFactor = double("q_factor")
    val payloadJson = text("payload_json")
    val loadPayloadJson = text("loads_json").default("{}")
}

object StoriesTable : LongIdTable("StoriesTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val storyName = varchar("story_name", 120)
    val heightH = double("height_h")
    val elevationZ = double("elevation_z")
    val isPenthouse = bool("is_penthouse").default(false)
}

object ColumnFamiliesTable : LongIdTable("ColumnFamiliesTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val familyName = varchar("family_name", 160)
    val b1 = double("b1")
    val h1 = double("h1")
    val isWallBoundary = bool("is_wall_boundary").default(false)
    val rebarAdopted = varchar("rebar_adopted", 200).nullable()
    val ndMax = double("nd_max").nullable()
    val nuRatio = double("nu_ratio").nullable()
    val shape = varchar("shape", 32).default(ColumnShape.RECTANGULAR.name)
    val diameter = double("diameter_cm").nullable()
    val clearHeight = double("clear_height_m").default(0.0)
    val elementIdsJson = text("element_ids_json").default("[]")
}

object BeamFamiliesTable : LongIdTable("BeamFamiliesTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val familyName = varchar("family_name", 160)
    val b = double("b")
    val h = double("h")
    val maxSpan = double("max_span")
    val rebarSup = varchar("rebar_sup", 200).nullable()
    val rebarInf = varchar("rebar_inf", 200).nullable()
    val role = varchar("role", 32).default(BeamRole.MAIN_BEAM.name)
    val minSpan = double("min_span").default(0.0)
    val supportWidth = double("support_width").default(0.0)
    val elementIdsJson = text("element_ids_json").default("[]")
}

object ShearWallsTable : LongIdTable("ShearWallsTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val pierName = varchar("pier_name", 160)
    val lw = double("lw")
    val bw = double("bw")
    val classification = varchar("classification", 80)
    val vdRatio = double("vd_ratio").nullable()
    val tauB = double("tau_b").nullable()
    val rebarAdopted = varchar("rebar_adopted", 200).nullable()
    val clearHeight = double("clear_height_m").default(0.0)
    val totalHeight = double("total_height_m").default(0.0)
    val direction = varchar("direction", 16).default("X")
    val elementIdsJson = text("element_ids_json").default("[]")
}

object CalculationResultsTable : LongIdTable("CalculationResultsTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val chapterId = integer("chapter_id")
    val title = varchar("title", 255).default("Calcul")
    val jsonPayload = text("json_payload")
    val createdAt = varchar("created_at", 40)
}

// Tables complementaires necessaires a une restitution complete du projet.
object GridLinesTable : LongIdTable("GridLinesTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val direction = varchar("direction", 8)
    val label = varchar("label", 32)
    val coordinate = double("coordinate_m")
    val isPrimary = bool("is_primary").default(true)
}

object SlabFamiliesTable : LongIdTable("SlabFamiliesTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val familyName = varchar("family_name", 160)
    val totalThickness = double("total_thickness_cm")
    val compressionThickness = double("compression_thickness_cm")
    val hollowBlockHeight = double("hollow_block_height_cm")
    val ribWidth = double("rib_width_cm")
    val ribSpacing = double("rib_spacing_cm")
    val isSolid = bool("is_solid").default(false)
    val elementIdsJson = text("element_ids_json").default("[]")
}

object JointLoadsTable : LongIdTable("JointLoadsTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val loadName = varchar("load_name", 120)
    val loadValue = varchar("load_value", 255)
}

object ManualRebarOverridesTable : LongIdTable("ManualRebarOverridesTable") {
    val jointId = reference("joint_id", StructuralJoints, onDelete = ReferenceOption.CASCADE)
    val elementType = varchar("element_type", 32)
    val familyName = varchar("family_name", 160)
    val adoptedRebar = varchar("adopted_rebar", 200)
    val secondaryRebar = varchar("secondary_rebar", 200).nullable()
    val axialKN = double("axial_kn").nullable()
    val utilization = double("utilization").nullable()
    val shearStressMPa = double("shear_stress_mpa").nullable()
    val notes = text("notes").default("")
}

/**
 * Persistance locale SQLite (JetBrains Exposed) d'un projet multi-joints. Le schema
 * normalise expose les familles et niveaux aux outils de reporting, tandis que le
 * payload JSON du joint garantit une restauration sans perte de la configuration
 * sismique, des grilles, sections, materiaux et modifications manuelles.
 */
class ProjectDatabaseManager private constructor(
    private val jdbcUrl: String,
    private val database: Database
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun <T> dbTransaction(block: Transaction.() -> T): T = transaction(database) {
        block(this)
    }

    fun saveProject(project: StoredProject): Long = dbTransaction {
        val now = Instant.now()
        val projectRef = if (project.id == null) {
            Projects.insertAndGetId {
                it[title] = project.title.ifBlank { "Projet sans titre" }
                it[wilaya] = project.wilaya
                it[engineerName] = project.engineerName
                it[clientName] = project.clientName
                it[reference] = project.reference
                it[createdAt] = now.toString()
                it[updatedAt] = now.toString()
            }
        } else {
            val existingId = project.id
            require(existingId > 0) { "Identifiant de projet invalide." }
            val updated = Projects.update({ Projects.id eq existingId }) {
                it[title] = project.title.ifBlank { "Projet sans titre" }
                it[wilaya] = project.wilaya
                it[engineerName] = project.engineerName
                it[clientName] = project.clientName
                it[reference] = project.reference
                it[updatedAt] = now.toString()
            }
            require(updated == 1) { "Projet $existingId introuvable." }
            EntityID(existingId, Projects)
        }

        StructuralJoints.deleteWhere { StructuralJoints.projectId eq projectRef }
        project.joints.forEach { archive ->
            insertJoint(projectRef, archive)
        }
        Projects.update({ Projects.id eq projectRef }) { it[updatedAt] = now.toString() }
        projectRef.value
    }

    fun saveProject(
        project: MasterProject,
        engineerName: String = "",
        clientName: String = "",
        reference: String = "",
        projectId: Long? = null
    ): Long = saveProject(
        StoredProject(
            id = projectId,
            title = project.projectTitle,
            wilaya = project.wilaya,
            engineerName = engineerName,
            clientName = clientName,
            reference = reference,
            joints = project.joints.map { JointArchive(it) }
        )
    )

    fun loadProject(projectId: Long): StoredProject? = dbTransaction {
        val projectRow = Projects.selectAll()
            .where { Projects.id eq EntityID(projectId, Projects) }
            .singleOrNull()
            ?: return@dbTransaction null
        val projectRef = projectRow[Projects.id]
        val jointRows = StructuralJoints.selectAll()
            .where { StructuralJoints.projectId eq projectRef }
            .orderBy(StructuralJoints.id, SortOrder.ASC)
            .toList()
        val joints = jointRows.map { row ->
            val jointRef = row[StructuralJoints.id]
            val payload = row[StructuralJoints.payloadJson]
            val joint = decodeJoint(payload, row)
            JointArchive(
                joint = joint,
                jointId = jointRef.value,
                loads = loadLoads(jointRef),
                manualRebars = loadManualRebars(jointRef),
                calculations = loadCalculationsForJoint(jointRef.value)
            )
        }
        StoredProject(
            id = projectRef.value,
            title = projectRow[Projects.title],
            wilaya = projectRow[Projects.wilaya],
            engineerName = projectRow[Projects.engineerName],
            clientName = projectRow[Projects.clientName],
            reference = projectRow[Projects.reference],
            createdAt = parseInstant(projectRow[Projects.createdAt]),
            updatedAt = parseInstant(projectRow[Projects.updatedAt]),
            joints = joints
        )
    }

    fun listProjects(): List<ProjectSummary> = dbTransaction {
        Projects.selectAll()
            .orderBy(Projects.updatedAt, SortOrder.DESC)
            .map { row ->
                val projectRef = row[Projects.id]
                val count = StructuralJoints.selectAll()
                    .where { StructuralJoints.projectId eq projectRef }
                    .count()
                    .toInt()
                ProjectSummary(
                    id = projectRef.value,
                    title = row[Projects.title],
                    wilaya = row[Projects.wilaya],
                    engineerName = row[Projects.engineerName],
                    updatedAt = parseInstant(row[Projects.updatedAt]),
                    jointCount = count
                )
            }
    }

    fun deleteProject(projectId: Long): Boolean = dbTransaction {
        Projects.deleteWhere { Projects.id eq EntityID(projectId, Projects) } > 0
    }

    fun appendCalculation(
        projectId: Long,
        jointName: String,
        chapterId: Int,
        title: String,
        jsonPayload: String
    ): Long = dbTransaction {
        val projectRef = EntityID(projectId, Projects)
        val jointRow = StructuralJoints.selectAll()
            .where { StructuralJoints.projectId eq projectRef }
            .firstOrNull { it[StructuralJoints.jointName].equals(jointName, ignoreCase = true) }
            ?: error("Joint '$jointName' introuvable dans le projet $projectId.")
        val now = Instant.now()
        val id = CalculationResultsTable.insertAndGetId {
            it[jointId] = jointRow[StructuralJoints.id]
            it[this.chapterId] = chapterId
            it[this.title] = title
            it[this.jsonPayload] = jsonPayload
            it[createdAt] = now.toString()
        }
        Projects.update({ Projects.id eq projectRef }) { it[updatedAt] = now.toString() }
        id.value
    }

    fun loadCalculations(jointId: Long): List<CalculationRecord> = dbTransaction {
        loadCalculationsForJoint(jointId)
    }

    fun latestCalculation(jointId: Long, chapterId: Int? = null): CalculationRecord? = dbTransaction {
        val rows = CalculationResultsTable.selectAll()
            .where { CalculationResultsTable.jointId eq EntityID(jointId, StructuralJoints) }
            .orderBy(CalculationResultsTable.id, SortOrder.DESC)
            .toList()
        rows.asSequence()
            .filter { chapterId == null || it[CalculationResultsTable.chapterId] == chapterId }
            .map { it.toCalculationRecord(jointId) }
            .firstOrNull()
    }

    fun close() {
        TransactionManager.closeAndUnregister(database)
    }

    // ------------------------------------------------------------------ writing

    private fun insertJoint(projectRef: EntityID<Long>, archive: JointArchive) {
        val joint = archive.joint
        val resolved = runCatching { Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint) }.getOrNull()
        val payload = json.encodeToString(StructuralJoint.serializer(), joint)
        val loadsPayload = json.encodeToString(archive.loads)
        val jointRef = StructuralJoints.insertAndGetId {
            it[projectId] = projectRef
            it[jointName] = joint.jointName
            it[seismicZone] = joint.seismicParams.zoneCode
            it[siteClass] = joint.seismicParams.siteClassCode
            it[importanceGroup] = joint.seismicParams.importanceGroupCode
            it[bracingSystem] = joint.seismicParams.bracingSystemId
            it[bracingSystemLabel] = resolved?.bracingSystem?.description.orEmpty()
            it[rCoeff] = resolved?.behaviorFactorR ?: joint.seismicParams.behaviorFactorOverride ?: 0.0
            it[qFactor] = resolved?.qualityFactor?.qf ?: joint.seismicParams.qualityFactorOverride ?: 1.0
            it[payloadJson] = payload
            it[loadPayloadJson] = loadsPayload
        }
        insertStories(jointRef, joint)
        insertGrids(jointRef, joint.gridSystem)
        insertColumnFamilies(jointRef, archive)
        insertBeamFamilies(jointRef, archive)
        insertShearWalls(jointRef, archive)
        insertSlabs(jointRef, joint.sectionCatalog.slabs)
        insertLoads(jointRef, archive.loads)
        insertManualRebars(jointRef, archive.manualRebars)
        archive.calculations.forEach { calculation -> insertCalculation(jointRef, calculation) }
    }

    private fun insertStories(jointRef: EntityID<Long>, joint: StructuralJoint) {
        joint.storySystem.orderedStories.forEach { story ->
            StoriesTable.insert {
                it[jointId] = jointRef
                it[storyName] = story.storyName
                it[heightH] = story.heightHk
                it[elevationZ] = story.elevationZk
                it[isPenthouse] = story.isPenthouseOrCage
            }
        }
    }

    private fun insertGrids(jointRef: EntityID<Long>, grid: GridSystem) {
        grid.sortedXGrids.forEach { line -> insertGrid(jointRef, "X", line) }
        grid.sortedYGrids.forEach { line -> insertGrid(jointRef, "Y", line) }
    }

    private fun insertGrid(jointRef: EntityID<Long>, direction: String, line: GridLine) {
        GridLinesTable.insert {
            it[jointId] = jointRef
            it[this.direction] = direction
            it[label] = line.label
            it[coordinate] = line.coordinateMeters
            it[isPrimary] = line.isPrimary
        }
    }

    private fun insertColumnFamilies(jointRef: EntityID<Long>, archive: JointArchive) {
        archive.joint.sectionCatalog.columns.forEach { column ->
            val override = archive.manualRebars.rebarOverride(RebarElementType.COLUMN, column.name)
            ColumnFamiliesTable.insert {
                it[jointId] = jointRef
                it[familyName] = column.name
                it[b1] = column.b1Cm
                it[h1] = column.h1Cm
                it[isWallBoundary] = column.isWallBoundary
                it[rebarAdopted] = override?.adoptedRebar
                it[ndMax] = override?.axialKN
                it[nuRatio] = override?.utilization
                it[shape] = column.shape.name
                it[diameter] = column.diameterCm
                it[clearHeight] = column.clearHeightLclMeters
                it[elementIdsJson] = json.encodeToString(column.elementIds)
            }
        }
    }

    private fun insertBeamFamilies(jointRef: EntityID<Long>, archive: JointArchive) {
        archive.joint.sectionCatalog.beams.forEach { beam ->
            val override = archive.manualRebars.rebarOverride(RebarElementType.BEAM, beam.name)
            BeamFamiliesTable.insert {
                it[jointId] = jointRef
                it[familyName] = beam.name
                it[b] = beam.bCm
                it[h] = beam.hCm
                it[maxSpan] = beam.maxClearSpanMeters
                it[rebarSup] = override?.adoptedRebar
                it[rebarInf] = override?.secondaryRebar
                it[role] = beam.role.name
                it[minSpan] = beam.minClearSpanMeters
                it[supportWidth] = beam.maxSupportWidthCm
                it[elementIdsJson] = json.encodeToString(beam.elementIds)
            }
        }
    }

    private fun insertShearWalls(jointRef: EntityID<Long>, archive: JointArchive) {
        archive.joint.sectionCatalog.shearWalls.forEach { wall ->
            val override = archive.manualRebars.rebarOverride(RebarElementType.WALL, wall.pierName)
            ShearWallsTable.insert {
                it[jointId] = jointRef
                it[pierName] = wall.pierName
                it[lw] = wall.lengthLwMeters
                it[bw] = wall.thicknessBwMeters
                it[classification] = wall.classification.name
                it[vdRatio] = override?.utilization
                it[tauB] = override?.shearStressMPa
                it[rebarAdopted] = override?.adoptedRebar
                it[clearHeight] = wall.clearHeightHeMeters
                it[totalHeight] = wall.totalHeightHwMeters
                it[direction] = wall.direction.name
                it[elementIdsJson] = json.encodeToString(wall.elementIds)
            }
        }
    }

    private fun insertSlabs(jointRef: EntityID<Long>, slabs: List<SlabStripTopology>) {
        slabs.forEach { slab ->
            SlabFamiliesTable.insert {
                it[jointId] = jointRef
                it[familyName] = slab.name
                it[totalThickness] = slab.totalThicknessHtCm
                it[compressionThickness] = slab.compressionSlabH0Cm
                it[hollowBlockHeight] = slab.hollowBlockHeightCm
                it[ribWidth] = slab.ribWidthB0Cm
                it[ribSpacing] = slab.ribSpacingCm
                it[isSolid] = slab.isSolidSlab
                it[elementIdsJson] = json.encodeToString(slab.elementIds)
            }
        }
    }

    private fun insertLoads(jointRef: EntityID<Long>, loads: Map<String, String>) {
        loads.forEach { (name, value) ->
            JointLoadsTable.insert {
                it[jointId] = jointRef
                it[loadName] = name
                it[loadValue] = value
            }
        }
    }

    private fun insertManualRebars(jointRef: EntityID<Long>, overrides: List<ManualRebarOverride>) {
        overrides.forEach { override ->
            ManualRebarOverridesTable.insert {
                it[jointId] = jointRef
                it[elementType] = override.elementType.name
                it[familyName] = override.familyName
                it[adoptedRebar] = override.adoptedRebar
                it[secondaryRebar] = override.secondaryRebar
                it[axialKN] = override.axialKN
                it[utilization] = override.utilization
                it[shearStressMPa] = override.shearStressMPa
                it[notes] = override.notes
            }
        }
    }

    private fun insertCalculation(jointRef: EntityID<Long>, calculation: CalculationRecord) {
        CalculationResultsTable.insert {
            it[jointId] = jointRef
            it[chapterId] = calculation.chapterId
            it[title] = calculation.title
            it[jsonPayload] = calculation.jsonPayload
            it[createdAt] = calculation.createdAt.toString()
        }
    }

    // ------------------------------------------------------------------ reading

    private fun decodeJoint(payload: String, row: org.jetbrains.exposed.sql.ResultRow): StructuralJoint {
        runCatching {
            return json.decodeFromString(StructuralJoint.serializer(), payload)
        }
        return fallbackJoint(row)
    }

    private fun fallbackJoint(row: org.jetbrains.exposed.sql.ResultRow): StructuralJoint {
        val jointRef = row[StructuralJoints.id]
        val stories = StoriesTable.selectAll()
            .where { StoriesTable.jointId eq jointRef }
            .orderBy(StoriesTable.elevationZ, SortOrder.ASC)
            .map {
                com.archilab.rpa2024.model.StoryLevel(
                    storyName = it[StoriesTable.storyName],
                    heightHk = it[StoriesTable.heightH],
                    elevationZk = it[StoriesTable.elevationZ],
                    isPenthouseOrCage = it[StoriesTable.isPenthouse]
                )
            }
        val xGrids = GridLinesTable.selectAll()
            .where { (GridLinesTable.jointId eq jointRef) and (GridLinesTable.direction eq "X") }
            .map { GridLine(it[GridLinesTable.label], it[GridLinesTable.coordinate], it[GridLinesTable.isPrimary]) }
        val yGrids = GridLinesTable.selectAll()
            .where { (GridLinesTable.jointId eq jointRef) and (GridLinesTable.direction eq "Y") }
            .map { GridLine(it[GridLinesTable.label], it[GridLinesTable.coordinate], it[GridLinesTable.isPrimary]) }
        val columns = ColumnFamiliesTable.selectAll()
            .where { ColumnFamiliesTable.jointId eq jointRef }
            .map {
                ColumnFamily(
                    name = it[ColumnFamiliesTable.familyName],
                    shape = runCatching { ColumnShape.valueOf(it[ColumnFamiliesTable.shape]) }.getOrDefault(ColumnShape.RECTANGULAR),
                    b1Cm = it[ColumnFamiliesTable.b1],
                    h1Cm = it[ColumnFamiliesTable.h1],
                    clearHeightLclMeters = it[ColumnFamiliesTable.clearHeight],
                    diameterCm = it[ColumnFamiliesTable.diameter],
                    isWallBoundary = it[ColumnFamiliesTable.isWallBoundary],
                    elementIds = decodeStringList(it[ColumnFamiliesTable.elementIdsJson])
                )
            }
        val beams = BeamFamiliesTable.selectAll()
            .where { BeamFamiliesTable.jointId eq jointRef }
            .map {
                BeamFamily(
                    name = it[BeamFamiliesTable.familyName],
                    role = runCatching { BeamRole.valueOf(it[BeamFamiliesTable.role]) }.getOrDefault(BeamRole.MAIN_BEAM),
                    bCm = it[BeamFamiliesTable.b],
                    hCm = it[BeamFamiliesTable.h],
                    maxClearSpanMeters = it[BeamFamiliesTable.maxSpan],
                    minClearSpanMeters = it[BeamFamiliesTable.minSpan],
                    maxSupportWidthCm = it[BeamFamiliesTable.supportWidth],
                    elementIds = decodeStringList(it[BeamFamiliesTable.elementIdsJson])
                )
            }
        val walls = ShearWallsTable.selectAll()
            .where { ShearWallsTable.jointId eq jointRef }
            .map {
                ShearWallFamily(
                    pierName = it[ShearWallsTable.pierName],
                    direction = com.archilab.rpa2024.model.AxisDirection.valueOf(it[ShearWallsTable.direction]),
                    lengthLwMeters = it[ShearWallsTable.lw],
                    thicknessBwMeters = it[ShearWallsTable.bw],
                    clearHeightHeMeters = it[ShearWallsTable.clearHeight],
                    totalHeightHwMeters = it[ShearWallsTable.totalHeight],
                    elementIds = decodeStringList(it[ShearWallsTable.elementIdsJson])
                )
            }
        val slabs = SlabFamiliesTable.selectAll()
            .where { SlabFamiliesTable.jointId eq jointRef }
            .map {
                SlabStripTopology(
                    name = it[SlabFamiliesTable.familyName],
                    totalThicknessHtCm = it[SlabFamiliesTable.totalThickness],
                    compressionSlabH0Cm = it[SlabFamiliesTable.compressionThickness],
                    hollowBlockHeightCm = it[SlabFamiliesTable.hollowBlockHeight],
                    ribWidthB0Cm = it[SlabFamiliesTable.ribWidth],
                    flangeWidthBCm = it[SlabFamiliesTable.ribSpacing],
                    ribSpacingCm = it[SlabFamiliesTable.ribSpacing],
                    isSolidSlab = it[SlabFamiliesTable.isSolid],
                    elementIds = decodeStringList(it[SlabFamiliesTable.elementIdsJson])
                )
            }
        val seismic = SeismicConfig(
            zoneCode = row[StructuralJoints.seismicZone],
            siteClassCode = row[StructuralJoints.siteClass],
            importanceGroupCode = row[StructuralJoints.importanceGroup],
            bracingSystemId = row[StructuralJoints.bracingSystem],
            behaviorFactorOverride = row[StructuralJoints.rCoeff].takeIf { it > 0.0 },
            qualityFactorOverride = row[StructuralJoints.qFactor].takeIf { it > 0.0 }
        )
        return StructuralJoint(
            jointName = row[StructuralJoints.jointName],
            gridSystem = GridSystem(xGrids = xGrids, yGrids = yGrids),
            storySystem = com.archilab.rpa2024.model.StorySystem(stories),
            sectionCatalog = SectionCatalog(columns = columns, beams = beams, shearWalls = walls, slabs = slabs),
            materials = com.archilab.rpa2024.model.MaterialConfig(),
            seismicParams = seismic
        )
    }

    private fun loadLoads(jointRef: EntityID<Long>): Map<String, String> =
        JointLoadsTable.selectAll()
            .where { JointLoadsTable.jointId eq jointRef }
            .associate { it[JointLoadsTable.loadName] to it[JointLoadsTable.loadValue] }

    private fun loadManualRebars(jointRef: EntityID<Long>): List<ManualRebarOverride> =
        ManualRebarOverridesTable.selectAll()
            .where { ManualRebarOverridesTable.jointId eq jointRef }
            .map {
                ManualRebarOverride(
                    elementType = runCatching {
                        RebarElementType.valueOf(it[ManualRebarOverridesTable.elementType])
                    }.getOrDefault(RebarElementType.SECONDARY),
                    familyName = it[ManualRebarOverridesTable.familyName],
                    adoptedRebar = it[ManualRebarOverridesTable.adoptedRebar],
                    secondaryRebar = it[ManualRebarOverridesTable.secondaryRebar],
                    axialKN = it[ManualRebarOverridesTable.axialKN],
                    utilization = it[ManualRebarOverridesTable.utilization],
                    shearStressMPa = it[ManualRebarOverridesTable.shearStressMPa],
                    notes = it[ManualRebarOverridesTable.notes]
                )
            }

    private fun loadCalculationsForJoint(jointId: Long): List<CalculationRecord> =
        CalculationResultsTable.selectAll()
            .where { CalculationResultsTable.jointId eq EntityID(jointId, StructuralJoints) }
            .orderBy(CalculationResultsTable.chapterId, SortOrder.ASC)
            .map { it.toCalculationRecord(jointId) }

    private fun org.jetbrains.exposed.sql.ResultRow.toCalculationRecord(jointId: Long): CalculationRecord =
        CalculationRecord(
            id = this[CalculationResultsTable.id].value,
            jointId = jointId,
            chapterId = this[CalculationResultsTable.chapterId],
            title = this[CalculationResultsTable.title],
            jsonPayload = this[CalculationResultsTable.jsonPayload],
            createdAt = parseInstant(this[CalculationResultsTable.createdAt]) ?: Instant.now()
        )

    private fun List<ManualRebarOverride>.rebarOverride(
        type: RebarElementType,
        familyName: String
    ): ManualRebarOverride? = firstOrNull {
        it.elementType == type && it.familyName.equals(familyName, ignoreCase = true)
    }

    private fun decodeStringList(payload: String): List<String> = runCatching {
        json.decodeFromString<List<String>>(payload)
    }.getOrDefault(emptyList())

    private fun parseInstant(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    companion object {
        fun open(databaseFile: Path): ProjectDatabaseManager {
            databaseFile.toAbsolutePath().parent?.let(Files::createDirectories)
            val url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}?foreign_keys=on&busy_timeout=5000"
            val database = Database.connect(url, driver = "org.sqlite.JDBC")
            val manager = ProjectDatabaseManager(url, database)
            manager.initializeSchema()
            return manager
        }
    }

    private fun initializeSchema() {
        dbTransaction {
            SchemaUtils.create(
                Projects,
                StructuralJoints,
                StoriesTable,
                GridLinesTable,
                ColumnFamiliesTable,
                BeamFamiliesTable,
                ShearWallsTable,
                SlabFamiliesTable,
                JointLoadsTable,
                ManualRebarOverridesTable,
                CalculationResultsTable
            )
        }
    }
}
