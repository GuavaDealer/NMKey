rootProject.name = "NMKey"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "Releases"
            url = uri("https://maven.guavadealer.com/releases")
        }
        maven {
            name = "Snapshots"
            url = uri("https://maven.guavadealer.com/snapshots")
        }
        mavenLocal()
    }
}

plugins {
    id("com.gradle.develocity") version "4.3.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        publishing.onlyIf { true }
    }
}
