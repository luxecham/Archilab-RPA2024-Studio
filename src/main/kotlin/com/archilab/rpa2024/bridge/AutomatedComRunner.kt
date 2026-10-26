package com.archilab.rpa2024.bridge

import com.archilab.rpa2024.parser.E2kModelParser
import com.archilab.rpa2024.parser.UnitSystem
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Dispatch
import com.sun.jna.platform.win32.COM.IDispatch
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.OaIdl
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.Variant
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

enum class CsiProduct { ETABS, SAFE }
enum class CsiExecutionMode { LEGACY_TEXT, OAPI_XLSX }

data class CsiVersion(val major: Int, val minor: Int, val patch: Int = 0, val raw: String) : Comparable<CsiVersion> {
    override fun compareTo(other: CsiVersion): Int = compareValuesBy(this, other, CsiVersion::major, CsiVersion::minor, CsiVersion::patch)

    companion object {
        fun parse(raw: String): CsiVersion {
            val match = Regex("(?<!\\d)(\\d{1,2})(?:\\.(\\d{1,2}))?(?:\\.(\\d{1,2}))?(?!\\d)").find(raw)
                ?: throw IllegalArgumentException("Version CSI illisible : '$raw'.")
            return CsiVersion(match.groupValues[1].toInt(), match.groupValues[2].toIntOrNull() ?: 0,
                match.groupValues[3].toIntOrNull() ?: 0, raw)
        }
    }
}

data class AutomatedRunRequest(
    val product: CsiProduct,
    val modelFile: Path,
    val projectTempDirectory: Path,
    val versionHint: String? = null,
    val executable: Path? = null,
    val tables: List<String> = AutomatedComRunner.defaultTables(product),
    val runAnalysis: Boolean = true,
    val saveModel: Boolean = true,
    val processTimeout: Duration = Duration.ofHours(2)
) {
    init {
        require(tables.isNotEmpty()) { "Au moins une table CSI doit être demandée." }
        require(!processTimeout.isNegative && !processTimeout.isZero) { "Le délai d'analyse doit être positif." }
    }
}

data class AutomatedRunResult(
    val product: CsiProduct,
    val version: CsiVersion,
    val mode: CsiExecutionMode,
    val exportDirectory: Path,
    val exportedTables: Map<String, Path>,
    val exportFailures: Map<String, String>,
    val log: List<String>
) {
    val isComplete: Boolean get() = exportFailures.isEmpty() && exportedTables.isNotEmpty()
}

interface CsiSession : AutoCloseable {
    val version: CsiVersion
    fun openModel(path: Path)
    fun analyzeAll()
    fun saveModel()
    fun exportTable(tableName: String, destination: Path)
    override fun close()
}

interface CsiSessionFactory {
    fun create(request: AutomatedRunRequest): CsiSession
}

class AutomatedComRunner(private val factory: CsiSessionFactory = WindowsCsiSessionFactory()) {
    suspend fun run(request: AutomatedRunRequest): AutomatedRunResult = withContext(Dispatchers.IO) {
        require(request.modelFile.exists()) { "Modèle CSI introuvable : ${request.modelFile}" }
        Files.createDirectories(request.projectTempDirectory)
        val session = factory.create(request)
        val mode = executionMode(request.product, session.version)
        validateVersion(request.product, session.version)
        val exportDirectory = request.projectTempDirectory.resolve(
            "${request.product.name.lowercase()}-${System.currentTimeMillis()}"
        )
        Files.createDirectories(exportDirectory)
        val log = mutableListOf<String>()
        val exported = linkedMapOf<String, Path>()
        val failures = linkedMapOf<String, String>()
        try {
            log += "Version ${session.version.raw} — mode $mode."
            session.openModel(request.modelFile)
            log += "Modèle ouvert : ${request.modelFile.fileName}."
            if (request.runAnalysis) {
                session.analyzeAll()
                log += "Analyse terminée."
            }
            if (request.saveModel) {
                session.saveModel()
                log += "Modèle enregistré."
            }
            request.tables.distinct().forEach { table ->
                val extension = if (mode == CsiExecutionMode.OAPI_XLSX) "xlsx" else "txt"
                val destination = exportDirectory.resolve("${fileId(table)}.$extension")
                try {
                    session.exportTable(table, destination)
                    if (!destination.exists() || Files.size(destination) == 0L) {
                        throw IllegalStateException("Export vide ou absent.")
                    }
                    exported[table] = destination
                } catch (error: Exception) {
                    failures[table] = error.message ?: error.javaClass.simpleName
                }
            }
        } finally {
            try {
                session.close()
                log += "Session CSI libérée."
            } catch (error: Exception) {
                log += "Libération CSI : ${error.message ?: error.javaClass.simpleName}."
            }
        }
        AutomatedRunResult(request.product, session.version, mode, exportDirectory, exported, failures, log)
    }

    fun start(scope: CoroutineScope, request: AutomatedRunRequest): Deferred<AutomatedRunResult> =
        scope.async(Dispatchers.IO) { run(request) }

    companion object {
        fun executionMode(product: CsiProduct, version: CsiVersion): CsiExecutionMode =
            if (product == CsiProduct.ETABS && version.major < 18) CsiExecutionMode.LEGACY_TEXT else CsiExecutionMode.OAPI_XLSX

        fun validateVersion(product: CsiProduct, version: CsiVersion) {
            val supported = when (product) {
                CsiProduct.ETABS -> version >= CsiVersion(9, 7, 4, "9.7.4") && version.major <= 21
                CsiProduct.SAFE -> version.major in 12..20
            }
            require(supported) { "${product.name} ${version.raw} n'est pas dans la plage prise en charge." }
        }

        fun defaultTables(product: CsiProduct): List<String> = when (product) {
            CsiProduct.ETABS -> listOf(
                "Program Control", "Story Definitions", "Grid Definitions - Lines", "Joint Coordinates",
                "Connectivity - Frame", "Connectivity - Area", "Frame Section Property Definitions",
                "Wall Slab Deck Section Properties", "Material Property Data", "Frame Assignments - Section",
                "Area Assignments - Section", "Wall Pier Labels", "Load Pattern Definitions",
                "Load Combination Definitions", "Modal Participating Mass Ratios",
                "Response Spectrum Base Reactions", "Base Reactions", "Joint Reactions", "Story Shears",
                "Centers Of Mass And Rigidity",
                "Joint Displacements", "Diaphragm Center Of Mass Displacements", "Column Forces",
                "Beam Forces", "Pier Forces"
            )
            CsiProduct.SAFE -> listOf(
                "Program Control", "Material Property Data", "Beam Properties", "Line Spring Properties",
                "Point Coordinates", "Joint Displacements", "Beam Forces", "Soil Pressure Summary"
            )
        }
    }
}

class WindowsCsiSessionFactory : CsiSessionFactory {
    override fun create(request: AutomatedRunRequest): CsiSession {
        val version = request.versionHint?.let(CsiVersion::parse) ?: detectVersion(request)
        return when (AutomatedComRunner.executionMode(request.product, version)) {
            CsiExecutionMode.LEGACY_TEXT -> LegacyTextCsiSession(
                version,
                requireNotNull(request.executable) { "ETABS < 18 : chemin de l'exécutable requis pour lancer l'analyse." },
                request.processTimeout,
                request.projectTempDirectory
            )
            CsiExecutionMode.OAPI_XLSX -> {
                check(System.getProperty("os.name").contains("Windows", true)) { "Le pilotage OAPI exige Windows." }
                JnaCsiSession(request.product, version)
            }
        }
    }

    private fun detectVersion(request: AutomatedRunRequest): CsiVersion {
        if (request.modelFile.extension.lowercase() in setOf("e2k", "s2k", "txt")) {
            val text = E2kModelParser.readText(request.modelFile)
            Regex("(?i)(?:ProgramVersion|Version|CurrVersion)\\s*=\\s*\"?([^\"\\r\\n]+)").find(text)?.let {
                return CsiVersion.parse(it.groupValues[1])
            }
            Regex("(?i)${request.product.name}\\s+(\\d+\\.\\d+(?:\\.\\d+)?)").find(text)?.let {
                return CsiVersion.parse(it.groupValues[1])
            }
        }
        return if (request.product == CsiProduct.ETABS) CsiVersion(21, 0, 0, "21") else CsiVersion(20, 0, 0, "20")
    }
}

private class LegacyTextCsiSession(
    override val version: CsiVersion,
    private val executable: Path,
    private val timeout: Duration,
    private val tempDirectory: Path
) : CsiSession {
    private var model: Path? = null
    private var process: Process? = null

    override fun openModel(path: Path) {
        model = path.toAbsolutePath().normalize()
    }

    override fun analyzeAll() {
        val model = requireNotNull(model)
        val log = Files.createTempFile(tempDirectory, "etabs-legacy-", ".log").toFile()
        val started = ProcessBuilder(executable.toAbsolutePath().toString(), model.toString())
            .redirectErrorStream(true).redirectOutput(log).start()
        process = started
        if (!started.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            started.destroy()
            throw IllegalStateException("Délai dépassé pour ETABS ${version.raw} ; journal : ${log.absolutePath}")
        }
        check(started.exitValue() == 0) {
            "ETABS ${version.raw} a retourné le code ${started.exitValue()} ; journal : ${log.absolutePath}"
        }
    }

    override fun saveModel() = Unit

    override fun exportTable(tableName: String, destination: Path) {
        val model = requireNotNull(model)
        val id = fileId(tableName)
        val candidates = listOf(
            model.parent.resolve("$id.txt"),
            model.parent.resolve("${model.nameWithoutExtension}_$id.txt"),
            tempDirectory.resolve("$id.txt")
        )
        val source = candidates.firstOrNull { it.exists() } ?: Files.list(model.parent).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension.equals("txt", true) }
                .filter { fileId(it.nameWithoutExtension).endsWith(id) }
                .findFirst().orElse(null)
        } ?: throw IllegalStateException("Export texte '$tableName' introuvable après analyse legacy.")
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun close() {
        process?.takeIf { it.isAlive }?.destroy()
        process = null
    }
}

class JnaCsiSession(
    private val product: CsiProduct,
    override val version: CsiVersion
) : CsiSession {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rpa2024-${product.name.lowercase()}-com").apply { isDaemon = true }
    }
    private var root: Dispatch? = null
    private var closed = false

    fun etabsLiveAdapter(): EtabsLiveAdapter {
        check(product == CsiProduct.ETABS) { "Cet adaptateur est réservé à ETABS." }
        return JnaEtabsLiveAdapter(this)
    }

    override fun openModel(path: Path) = onCom {
        attach()
        call("SapModel.File.OpenFile", path.toAbsolutePath().normalize().toString())
    }

    override fun analyzeAll() = onCom {
        attach()
        call("SapModel.Analyze.RunAnalysis")
    }

    override fun saveModel() = onCom {
        attach()
        call("SapModel.File.Save")
    }

    override fun exportTable(tableName: String, destination: Path) {
        val csv = Files.createTempFile(destination.parent, "csi-table-", ".csv")
        try {
            onCom {
                attach()
                call(
                    "SapModel.DatabaseTables.GetTableForDisplayCSVFile",
                    tableName, emptyArray<String>(), "All", 0, emptyArray<String>(), 0,
                    emptyArray<String>(), csv.toAbsolutePath().toString()
                )
            }
            check(csv.exists() && Files.size(csv) > 0L) { "Table '$tableName' vide ou indisponible via OAPI." }
            csvToXlsx(csv, destination, tableName)
        } finally {
            Files.deleteIfExists(csv)
        }
    }

    internal fun invoke(path: String, vararg args: Any?): Any? = onCom {
        attach()
        val segments = path.split('.')
        var owner = root!!
        try {
            for (segment in segments.dropLast(1)) {
                val next = invokeDispatch(owner, segment, emptyArray(), OleAuto.DISPATCH_PROPERTYGET) as? Dispatch
                    ?: throw IllegalStateException("Propriété COM '$segment' indisponible.")
                if (owner != root) owner.Release()
                owner = next
            }
            invokeDispatch(owner, segments.last(), args, OleAuto.DISPATCH_METHOD)
        } finally {
            if (owner != root) owner.Release()
        }
    }

    override fun close() {
        if (closed) return
        try {
            onCom {
                root?.Release()
                root = null
                Ole32.INSTANCE.CoUninitialize()
            }
        } finally {
            closed = true
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun attach() {
        if (root != null) return
        val initialized = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        COMUtils.checkRC(initialized)
        val progId = if (product == CsiProduct.ETABS) "CSI.ETABS.API.ETABSObject" else "CSI.SAFE.API.SAFEObject"
        val clsid = Guid.CLSID.ByReference()
        COMUtils.checkRC(Ole32.INSTANCE.CLSIDFromProgID(progId, clsid))
        val reference = PointerByReference()
        val active = OleAuto.INSTANCE.GetActiveObject(clsid, null, reference)
        if (COMUtils.FAILED(active)) {
            COMUtils.checkRC(Ole32.INSTANCE.CoCreateInstance(
                clsid, null, 0x17, Guid.IID(IDispatch.IID_IDISPATCH), reference
            ))
        }
        root = Dispatch(reference.value)
    }

    private fun <T> onCom(action: () -> T): T {
        check(!closed) { "Session CSI fermée." }
        return executor.submit(Callable(action)).get()
    }

    private fun call(path: String, vararg args: Any?) {
        val result = invoke(path, *args)
        if (result is Number) check(result.toInt() == 0) { "Échec OAPI '$path' (code ${result.toInt()})." }
    }
}

private class JnaEtabsLiveAdapter(private val session: JnaCsiSession) : EtabsLiveAdapter {
    private val combinations = mutableSetOf<String>()

    override fun setMaterial(material: EtabsMaterialProperties, units: UnitSystem) {
        val type = if (material.type == "CONCRETE") 2 else 6
        call("SapModel.PropMaterial.SetMaterial", material.name, type, -1, "", "")
        call("SapModel.PropMaterial.SetMPIsotropic", material.name,
            CsiUnits.stress(material.elasticModulusKNM2, units), material.poisson, material.thermalCoefficient)
        call("SapModel.PropMaterial.SetWeightAndMass", material.name, 1,
            CsiUnits.weightDensity(material.weightDensityKNM3, units))
        call("SapModel.PropMaterial.SetWeightAndMass", material.name, 2,
            CsiUnits.massDensity(material.massDensityTonsM3, units))
        if (material.type == "CONCRETE") {
            call("SapModel.PropMaterial.SetOConcrete", material.name,
                CsiUnits.stress(material.compressiveStrengthKNM2 ?: 25_000.0, units), false, 0.0, 0.0, 0, 0)
        } else {
            val fy = CsiUnits.stress(material.yieldStrengthKNM2 ?: 500_000.0, units)
            val fu = CsiUnits.stress(material.tensileStrengthKNM2 ?: fy, units)
            call("SapModel.PropMaterial.SetORebar", material.name, fy, fu, fy, fu, 0, 0)
        }
    }

    override fun setMassSource(source: EtabsMassSource) {
        call("SapModel.SourceMass.SetMassSource_1", source.name, false, false, true, 2,
            arrayOf(source.permanentPattern, source.livePattern),
            doubleArrayOf(source.permanentMultiplier, source.liveMultiplier),
            source.lateralMassOnly, source.lumpAtStories)
    }

    override fun setResponseSpectrumFunction(function: EtabsSpectrumFunction) {
        call("SapModel.Func.FuncRS.SetFromFile", function.name, function.file.toString(), 0, 0, 0.05)
    }

    override fun setResponseSpectrumCase(case: EtabsSpectrumCase) {
        call("SapModel.LoadCases.ResponseSpectrum.SetCase", case.name)
        call("SapModel.LoadCases.ResponseSpectrum.SetLoads", case.name, 1, arrayOf(case.direction),
            arrayOf(case.functionName), doubleArrayOf(1.0), doubleArrayOf(0.0),
            doubleArrayOf(case.eccentricity), "Modal", -1.0, "GLOBAL")
    }

    override fun setLoadCombination(combination: EtabsLoadCombination) {
        call("SapModel.RespCombo.Add", combination.name, 0)
        combination.terms.forEach { term ->
            call("SapModel.RespCombo.SetCaseList", combination.name, if (term.load in combinations) 1 else 0,
                term.load, term.factor)
        }
        combinations += combination.name
    }

    private fun call(path: String, vararg args: Any?) {
        val result = session.invoke(path, *args)
        if (result is Number) check(result.toInt() == 0) { "Échec OAPI '$path' (code ${result.toInt()})." }
    }
}

private fun invokeDispatch(dispatch: Dispatch, name: String, args: Array<out Any?>, flags: Int): Any? {
    val dispId = OaIdl.DISPIDByReference()
    COMUtils.checkRC(dispatch.GetIDsOfNames(Guid.REFIID(), arrayOf(WString(name)), 1, WinDef.LCID(0x0409), dispId))
    val variants = args.toList().asReversed().map(::variant).toTypedArray()
    val params = OleAuto.DISPPARAMS.ByReference()
    val result = Variant.VARIANT.ByReference()
    val exception = OaIdl.EXCEPINFO.ByReference()
    val argumentError = com.sun.jna.ptr.IntByReference()
    OleAuto.INSTANCE.VariantInit(result)
    return try {
        params.setArgs(variants)
        params.write()
        val hr = dispatch.Invoke(dispId.value, Guid.REFIID(), WinDef.LCID(0x0409), WinDef.WORD(flags.toLong()),
            params, result, exception, argumentError)
        COMUtils.checkRC(hr, exception, argumentError)
        result.value
    } finally {
        variants.forEach { OleAuto.INSTANCE.VariantClear(it) }
        if (result.value !is Dispatch) OleAuto.INSTANCE.VariantClear(result)
    }
}

private fun variant(value: Any?): Variant.VARIANT = when (value) {
    null -> Variant.VARIANT()
    is Variant.VARIANT -> value
    is Dispatch -> Variant.VARIANT(value)
    is Array<*> -> Variant.VARIANT(safeArray(value))
    is DoubleArray -> Variant.VARIANT(safeArray(value))
    is IntArray -> Variant.VARIANT(safeArray(value))
    is String -> Variant.VARIANT(value)
    is Boolean -> Variant.VARIANT(value)
    is Int -> Variant.VARIANT(value)
    is Double -> Variant.VARIANT(value)
    is Long -> Variant.VARIANT(value)
    is Float -> Variant.VARIANT(value)
    is Short -> Variant.VARIANT(value)
    is Byte -> Variant.VARIANT(value)
    else -> throw IllegalArgumentException("Type VARIANT OAPI non pris en charge : ${value.javaClass.name}")
}

private fun safeArray(values: Array<*>): OaIdl.SAFEARRAY {
    require(values.all { it is String? }) { "Seuls les tableaux de chaînes ou de nombres sont acceptés par OAPI." }
    val array = OaIdl.SAFEARRAY.createSafeArray(WTypes.VARTYPE(8), values.size)
    values.forEachIndexed { index, value -> array.putElement(value as? String, index) }
    return array
}

private fun safeArray(values: DoubleArray): OaIdl.SAFEARRAY {
    val array = OaIdl.SAFEARRAY.createSafeArray(WTypes.VARTYPE(5), values.size)
    values.forEachIndexed { index, value -> array.putElement(value, index) }
    return array
}

private fun safeArray(values: IntArray): OaIdl.SAFEARRAY {
    val array = OaIdl.SAFEARRAY.createSafeArray(WTypes.VARTYPE(3), values.size)
    values.forEachIndexed { index, value -> array.putElement(value, index) }
    return array
}

private fun csvToXlsx(csv: Path, destination: Path, sheetName: String) {
    val lines = E2kModelParser.readText(csv).lineSequence().filter { it.isNotBlank() }.toList()
    XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet(sheetName.take(31).ifBlank { "CSI" })
        lines.forEachIndexed { rowIndex, line ->
            val row = sheet.createRow(rowIndex)
            parseCsv(line).forEachIndexed { columnIndex, value ->
                row.createCell(columnIndex).setCellValue(value)
            }
        }
        destination.parent?.let(Files::createDirectories)
        Files.newOutputStream(destination).use(workbook::write)
    }
}

private fun parseCsv(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                cell.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                cells += cell.toString().trim()
                cell.clear()
            }
            else -> cell.append(char)
        }
        index++
    }
    cells += cell.toString().trim()
    return cells
}

private fun fileId(value: String): String = value.trim()
    .replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "CSI_TABLE" }
