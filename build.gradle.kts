import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())

    compilerOptions {
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

dependencies {
    intellijPlatform {
        val localIdePath = providers.gradleProperty("localIdePath")
        if (localIdePath.isPresent) {
            local(localIdePath.get())
        } else {
            create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        }

        bundledPlugin("Git4Idea")
        bundledModules("intellij.platform.vcs.dvcs", "intellij.platform.vcs.dvcs.impl")

        pluginVerifier()
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    buildSearchableOptions = providers.gradleProperty("buildSearchableOptions").map(String::toBoolean).orElse(false)

    pluginVerification {
        ides {
            val localIdePath = providers.gradleProperty("localIdePath")
            if (localIdePath.isPresent) {
                local(localIdePath.get())
            } else {
                recommended()
            }
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
