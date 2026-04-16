import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val platformPath = providers.gradleProperty("platformPath")
    .orElse(providers.environmentVariable("IDEA_PLATFORM_PATH"))
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: error(
        "Missing IntelliJ platform path. Set 'platformPath' in ~/.gradle/gradle.properties " +
            "or export IDEA_PLATFORM_PATH before running Gradle."
    )

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.hierynomus:sshj:${providers.gradleProperty("sshjVersion").get()}")

    intellijPlatform {
        // Keep the shared build reproducible without committing contributor-specific IDE paths.
        local(platformPath)
        testFramework(TestFrameworkType.Platform)
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    wrapper {
        gradleVersion = "9.4.1"
    }
}
