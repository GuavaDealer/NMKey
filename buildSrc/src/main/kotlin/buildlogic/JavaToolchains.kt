package buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure

internal val java17: JavaLanguageVersion = JavaLanguageVersion.of(17)
internal val java25: JavaLanguageVersion = JavaLanguageVersion.of(25)

fun Project.configureNmKeyJavaToolchains() {
    extensions.configure<JavaPluginExtension> {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
        toolchain.languageVersion.set(java17)
    }
}
