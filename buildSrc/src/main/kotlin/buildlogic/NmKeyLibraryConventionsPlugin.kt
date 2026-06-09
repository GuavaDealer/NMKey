package buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class NmKeyLibraryConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureNmKeyJavaToolchains()
        target.registerPaperCompatibilityTasks()
        target.configureNmKeyArtifacts()
        target.configureNmKeyTests()
        target.configureNmKeyRunServer()
    }
}
