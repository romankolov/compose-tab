pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "compose-tab"
include(":score-json", ":score-model", ":score-ui", ":sample", ":sample-tuxguitar")
