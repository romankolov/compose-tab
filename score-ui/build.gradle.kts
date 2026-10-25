plugins {
    kotlin("multiplatform"); id("com.android.library")
    id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose")
}
kotlin {
    androidTarget(); jvm("desktop"); iosArm64(); iosSimulatorArm64(); iosX64()
    sourceSets { commonMain.dependencies {
        api(project(":score-model"))
        implementation(compose.runtime); implementation(compose.foundation)
        implementation(compose.ui); implementation(compose.animation)
    }; commonTest.dependencies { implementation(kotlin("test")) } }
}
android { namespace = "dev.composetab.ui"; compileSdk = 35; defaultConfig { minSdk = 24 } }
