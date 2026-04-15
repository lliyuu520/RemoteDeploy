import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.hierynomus:sshj:${providers.gradleProperty("sshjVersion").get()}")

    intellijPlatform {
        local(providers.gradleProperty("platformPath"))
        bundledPlugin("org.jetbrains.plugins.go")
        bundledPlugin("org.jetbrains.plugins.terminal")
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
