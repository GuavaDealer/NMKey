@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
