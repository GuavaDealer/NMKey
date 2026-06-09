package buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named

fun Project.configureNmKeyArtifacts() {
    tasks.matching { it.name == "sourcesJar" || it.name == "javadocJar" }.configureEach {
        enabled = false
    }

    tasks.named<Jar>("jar") {
        enabled = false
    }

    tasks.named("assemble") {
        dependsOn(tasks.named("shadowJar"))
    }
}
