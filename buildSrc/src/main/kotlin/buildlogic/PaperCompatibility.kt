package buildlogic

import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

private data class PaperCompatibilityTarget(
    val apiVersion: String,
    val jvmVersion: Int,
) {
    val safeName: String = apiVersion
        .replace(Regex("[^A-Za-z0-9]"), "_")
        .trim('_')
}

fun Project.registerPaperCompatibilityTasks() {
    val paperCompatibilityTargets = listOf(
        PaperCompatibilityTarget(libs.version("paper-api-compat-v1-v17"), 17),
        PaperCompatibilityTarget(libs.version("paper-api-compat-v1_v18v2"), 17),
        PaperCompatibilityTarget(libs.version("paper-api-compat-v1-v19v4"), 17),
        PaperCompatibilityTarget(libs.version("paper-api-compat-v1-v20v6"), 21),
        PaperCompatibilityTarget(libs.version("paper-api-compat-v1-v21v8"), 21),
        PaperCompatibilityTarget(libs.version("paper-api-compat-experimental"), 25),
    )

    val paperCompatibilityTasks = paperCompatibilityTargets.map { target ->
        val configuration = configurations.create("paperCompatibility${target.safeName}") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, target.jvmVersion)
            }
        }

        dependencies.add(configuration.name, "io.papermc.paper:paper-api:${target.apiVersion}")

        tasks.register<Sync>("verifyPaperCompatibility${target.safeName}") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Resolves Paper API ${target.apiVersion} for JVM ${target.jvmVersion} compatibility."
            outputs.upToDateWhen { false }

            from(configuration)
            into(layout.buildDirectory.dir("paper-compatibility/${target.safeName}"))
        }
    }

    tasks.register("verifyPaperCompatibility") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Resolves every declared Paper API compatibility target."
        dependsOn(paperCompatibilityTasks)
    }
}
