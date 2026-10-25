plugins {
    `maven-publish` kotlin("multiplatform"); id("com.android.library") }
kotlin {
    androidTarget { publishLibraryVariants("release") }; jvm("desktop"); iosArm64(); iosSimulatorArm64(); iosX64()
    sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}
android { namespace = "dev.composetab.model"; compileSdk = 35; defaultConfig { minSdk = 24 } }

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
