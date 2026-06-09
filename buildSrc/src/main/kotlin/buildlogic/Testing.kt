package buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

fun Project.configureNmKeyTests() {
    tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processTestResources") {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    tasks.named<Test>("test") {
        dependsOn("verifyPaperCompatibility")
        outputs.upToDateWhen { false }
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
        useJUnitPlatform {
            excludeTags("integration")
        }
    }

    val sourceSets = extensions.getByName<SourceSetContainer>("sourceSets")
    tasks.register<Test>("liveIntegrationTest") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs live NMCrate API integration tests. Requires network access and a valid bundled nmkey.txt."
        outputs.upToDateWhen { false }

        dependsOn("testClasses")
        shouldRunAfter("test")

        testClassesDirs = sourceSets.getByName("test").output.classesDirs
        classpath = sourceSets.getByName("test").runtimeClasspath

        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
        useJUnitPlatform {
            includeTags("integration")
        }
    }

    tasks.named("check") {
        dependsOn("test")
    }
}
