rootProject.name = "gitflow-integration"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets Gradle download the JDK requested by the toolchain when none matches locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
