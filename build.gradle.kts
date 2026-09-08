import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val localAndroidStudioPath = providers.gradleProperty("androidStudioPath").orNull
val releaseChannel = project.version.toString()
    .substringAfter('-', missingDelimiterValue = "")
    .substringBefore('.')
    .let { suffix -> if (suffix == "beta") "beta" else "default" }

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        if (localAndroidStudioPath != null) {
            local(localAndroidStudioPath)
        } else {
            intellijIdea("2025.2.6.2")
        }
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    publishing {
        channels = listOf(releaseChannel)
    }
}
