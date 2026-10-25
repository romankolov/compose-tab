plugins {
    `maven-publish`
    kotlin("multiplatform"); id("com.android.library")
    id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose")
}
kotlin {
    androidTarget { publishLibraryVariants("release") }; jvm("desktop"); iosArm64(); iosSimulatorArm64(); iosX64()
    sourceSets { commonMain.dependencies {
        api(project(":score-model"))
        api(compose.runtime); implementation(compose.foundation)
        api(compose.ui); implementation(compose.animation)
        implementation(compose.components.resources)
    }
        commonTest.dependencies { implementation(kotlin("test")) }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
android { namespace = "dev.composetab.ui"; compileSdk = 35; defaultConfig { minSdk = 24 } }

compose.resources { packageOfResClass = "dev.composetab.ui.resources" }

publishing {
    repositories {
        maven { name = "Build"; url = layout.buildDirectory.dir("repository").get().asFile.toURI() }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.name)
            description.set("UI-only Compose Multiplatform music notation and tablature")
            url.set("https://github.com/romankolov/compose-tab")
        }
    }
}
