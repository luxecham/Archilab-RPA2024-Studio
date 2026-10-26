import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.archilab.rpa2024"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // 1. Dépendances Compose Desktop (UI Windows Standalone)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // 2. Moteur de génération documentaire OpenXML Word (.docx) & OMML
    // Version 5.3.0 avec poi-ooxml-full indispensable pour manipuler le schéma XML OpenXML complet
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi-ooxml-full:5.3.0")
    implementation("org.apache.xmlbeans:xmlbeans:5.2.1")

    // 3. Traitement des fichiers tabulaires (ETABS / SAFE Excel exports)
    implementation("org.apache.commons:commons-csv:1.11.0")

    // 4. Coroutines pour l'exécution asynchrone du pipeline de calcul
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

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

    // 5. Banc d'essai et tests d'intégration unitaires
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(21)
}

// Configuration du packaging de l'exécutable Windows Desktop
compose.desktop {
    application {
        mainClass = "com.archilab.rpa2024.ArchilabMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Archilab-RPA2024-Studio"
            packageVersion = "1.0.0"
            description = "Logiciel d'automatisation de calcul parasismique RPA 2024 et note de calcul CTC"
            vendor = "BET ARCHILAB"
            copyright = "© 2026 BET ARCHILAB. Tous droits réservés."

            windows {
                menuGroup = "BET Archilab"
                perUserInstall = false
                shortcut = true
                dirChooser = true
                // Options JVM pour le traitement lourd de modèles volumineux
                jvmArgs.addAll(
                    listOf(
                        "-Xms512m",
                        "-Xmx4096m",
                        "-Dfile.encoding=UTF-8"
                    )
                )
            }
        }
    }
}
