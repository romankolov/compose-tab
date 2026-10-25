plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}
dependencies {
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("commons-io:commons-io:2.15.1")
}
