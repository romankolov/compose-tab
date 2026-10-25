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
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            implementation(compose.components.resources)
        }
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(project(":sample-tuxguitar")) }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        getByName("desktopMain").dependsOn(jvmSharedMain)
        androidMain.dependencies { implementation("androidx.activity:activity-compose:1.10.1") }
        androidInstrumentedTest.dependencies {
            implementation("androidx.test:runner:1.6.2")
            implementation("androidx.test.ext:junit:1.2.1")
            implementation("androidx.test:core:1.6.1")
            implementation("androidx.test.uiautomator:uiautomator:2.3.0")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
        }
    }
}
android {
    namespace = "dev.composetab.sample"; compileSdk = 35
    defaultConfig { applicationId = "dev.composetab.sample"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "0.3.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}
compose.desktop {
    application {
        mainClass = "dev.composetab.sample.MainKt"
        nativeDistributions {
            packageName = "ComposeTabStudio"
            packageVersion = "0.3.0"
            modules("java.xml", "java.desktop", "java.logging", "jdk.unsupported")
        }
    }
}

compose.resources { packageOfResClass = "dev.composetab.sample.resources" }
