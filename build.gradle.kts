import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.guavadealer.plugins.shared.missingProperty
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    `java-library`
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.gdPublish)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    id("nmkey-library-conventions")
}

group = project.findProperty("group")?.toString() ?: missingProperty("group")
version = project.findProperty("version")?.toString() ?: missingProperty("version")

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

@Suppress("VulnerableLibrariesLocal")
dependencies {
    api(libs.kotlinx.serialization.json)
    compileOnly(libs.paper.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.maven.resolver.provider)
    testRuntimeOnly(libs.maven.resolver.connector.basic)
    testRuntimeOnly(libs.maven.resolver.transport.http)
}

buildscript {
    configurations.all {
        resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.NANOSECONDS)
        resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.NANOSECONDS)
    }
}

tasks.clean {
    delete("build", "buildSrc/build")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    minimize {
        exclude(dependency("io.ktor:.*:.*"))
        exclude(dependency("org.jetbrains.kotlin:.*:.*"))
        exclude(dependency("org.jetbrains.kotlinx:.*:.*"))
    }

    mergeServiceFiles()
}

runPaper {
    disablePluginJarDetection()
}

tasks.named<RunServer>("runServer") {
    minecraftVersion(libs.versions.minecraft.get())
    pluginJars(tasks.named<Jar>("testPluginJar").flatMap { it.archiveFile })
    javaLauncher.set(
        project.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        },
    )
    jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}
