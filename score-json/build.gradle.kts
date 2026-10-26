plugins {
    id("maven-publish")
    kotlin("multiplatform")
    id("com.android.library")
}
kotlin {
    androidTarget { publishLibraryVariants("release") }; jvm("desktop"); iosArm64(); iosSimulatorArm64(); iosX64()
    sourceSets {
        commonMain.dependencies {
            api(project(":score-model"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
android { namespace = "dev.composetab.json"; compileSdk = 35; defaultConfig { minSdk = 24 } }

publishing {
    repositories {
        maven { name = "Build"; url = layout.buildDirectory.dir("repository").get().asFile.toURI() }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.name)
            description.set("Custom track JSON parser and Compose Tab model adapter")
            url.set("https://github.com/romankolov/compose-tab")
        }
    }
}

// Live contract inputs participate in test caching; normal unit tests need no network.
val linkedSamples = providers.environmentVariable("COMPOSE_TAB_JSON_SAMPLES")
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (linkedSamples.isPresent) inputs.dir(linkedSamples.get())
}
