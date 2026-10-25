plugins { kotlin("multiplatform"); id("com.android.library") }
kotlin {
    androidTarget(); jvm("desktop"); iosArm64(); iosSimulatorArm64(); iosX64()
    sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}
android { namespace = "dev.composetab.model"; compileSdk = 35; defaultConfig { minSdk = 24 } }
