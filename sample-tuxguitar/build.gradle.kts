plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}
tasks.jar {
    from(listOf("LICENSE", "README.md")) { into("META-INF/tuxguitar") }
}
tasks.named<Jar>("sourcesJar") {
    from(listOf("LICENSE", "README.md")) { into("META-INF/tuxguitar") }
}
