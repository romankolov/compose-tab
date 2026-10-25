plugins {
    kotlin("multiplatform"); id("com.android.application")
    id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose")
}
kotlin {
    androidTarget(); jvm("desktop")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework { baseName = "ComposeTabSample"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":score-ui")); implementation(compose.material3)
            implementation(compose.foundation); implementation(compose.animation)
        }
        androidMain.dependencies { implementation("androidx.activity:activity-compose:1.10.1") }
        getByName("desktopMain").dependencies { implementation(compose.desktop.currentOs) }
    }
}
android {
    namespace = "dev.composetab.sample"; compileSdk = 35
    defaultConfig { applicationId = "dev.composetab.sample"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}
compose.desktop { application { mainClass = "dev.composetab.sample.MainKt" } }
