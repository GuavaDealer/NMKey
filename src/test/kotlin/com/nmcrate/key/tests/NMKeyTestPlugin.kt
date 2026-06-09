package com.nmcrate.key.tests

import com.nmcrate.key.nmKey
import com.nmcrate.key.releaseNmKey
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

object NMKeyTestPlugin : JavaPlugin() {
    private const val PLUGIN_ID = "d486742f"
    private const val RECHECK_INTERVAL_TICKS = 6000L

    private var validationTask: BukkitTask? = null

    override fun onEnable() {
        if (!validateLicense()) return

        validationTask = server.scheduler.runTaskTimerAsynchronously(
            this,
            Runnable { validateLicense() },
            RECHECK_INTERVAL_TICKS,
            RECHECK_INTERVAL_TICKS,
        )
    }

    override fun onDisable() {
        validationTask?.cancel()
        validationTask = null

        runCatching {
            releaseNmKey(PLUGIN_ID)
        }.onFailure { error ->
            logger.warning("Failed to release license key: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private fun validateLicense(): Boolean = runCatching {
        val session = nmKey(PLUGIN_ID)
        session.disablePluginTaskIfInvalid()
    }.onFailure { error ->
        logger.warning("Failed to check license key: ${error.message ?: error::class.java.simpleName}")
        server.scheduler.runTask(
            this,
            Runnable {
                if (isEnabled) {
                    server.pluginManager.disablePlugin(this)
                }
            },
        )
    }.getOrDefault(false)
}
