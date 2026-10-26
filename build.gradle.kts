import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.archilab.rpa2024"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // Apache POI for Excel and Word (.docx, .xlsx, .xls)
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi-ooxml-full:5.3.0")

    // Apache PDFBox for PDF generation
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // Jackcess for MS Access (.mdb)
    implementation("com.healthmarketscience.jackcess:jackcess:4.0.7")

    // JetBrains Exposed ORM & SQLite JDBC
    val exposedVersion = "0.56.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")

    // JNA for Windows COM / OAPI Interoperability
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.16")

    // Testing
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

compose.desktop {
    application {
        mainClass = "com.archilab.rpa2024.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "RPA2024Desktop"
            packageVersion = "1.0.0"
            description = "Logiciel d'ingénierie parasismique RPA 2024 & Importateur ETABS"
            vendor = "ArchiLab Engineering"

            windows {
                menuGroup = "ArchiLab"
                upgradeUuid = "b3b40441-fc9a-4297-8c0c-46da09602145"
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}
