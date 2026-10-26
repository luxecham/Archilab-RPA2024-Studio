plugins {
    kotlin("multiplatform") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

kotlin {
    wasmJs {
        moduleName = "archilab-civilwork"
        browser {
            commonWebpackConfig {
                outputFileName = "archilab-civilwork.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            }
        }
    }
}
