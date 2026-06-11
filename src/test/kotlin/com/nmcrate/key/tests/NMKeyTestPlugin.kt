package com.nmcrate.key.tests

import com.nmcrate.key.nmKeyAsync
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

object NMKeyTestPlugin : JavaPlugin() {
    private const val PLUGIN_ID = "d486742f"
    private const val RECHECK_INTERVAL_TICKS = 6000L

    private var validationTask: BukkitTask? = null

    override fun onEnable() {
        validateLicense()

        validationTask = server.scheduler.runTaskTimer(
            this,
            Runnable { validateLicense() },
            RECHECK_INTERVAL_TICKS,
            RECHECK_INTERVAL_TICKS,
        )
    }

    override fun onDisable() {
        validationTask?.cancel()
        validationTask = null
    }

    private fun validateLicense() {
        runCatching {
            nmKeyAsync(PLUGIN_ID) { session ->
                session.disablePluginIfInvalid(this)
            }
        }.onFailure { error ->
            logger.warning("Failed to check license key: ${error.message ?: error::class.java.simpleName}")
            if (isEnabled) {
                server.pluginManager.disablePlugin(this)
            }
        }
    }
}
