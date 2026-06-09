package buildlogic

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

fun Project.configureNmKeyRunServer() {
    val sourceSets = extensions.getByName<SourceSetContainer>("sourceSets")
    val testPluginJar = tasks.register<Jar>("testPluginJar") {
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Builds a runnable Paper test plugin jar from the test harness."
        archiveClassifier.set("test-plugin")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(sourceSets.getByName("main").output)
        from(sourceSets.getByName("test").output)
        from(
            configurations.getByName("runtimeClasspath").map { file ->
                if (file.isDirectory) file else zipTree(file)
            },
        )

        dependsOn("testClasses")
    }

    tasks.named("runServer") {
        dependsOn(testPluginJar)
        dependsOn("liveIntegrationTest")
    }
}
