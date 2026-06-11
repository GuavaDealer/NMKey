package com.nmcrate.key

import org.bukkit.plugin.java.JavaPlugin

/**
 * Represents the result of an NMKey validation attempt and provides lifecycle management.
 *
 * This class is a pure data holder and does NOT hold a reference to the `JavaPlugin`.
 * It is completely safe to store this session in global/companion fields without
 * causing Bukkit classloader memory leaks during server reloads.
 *
 * @param pluginId The ID used for the validation request.
 * @param valid Whether the key validation was successful.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
data class NMKeySession(
    val pluginId: String,
    val valid: Boolean,
) {
    /**
     * Releases the active license seat for this plugin on the remote server.
     * This method executes synchronously and is safe to use in `onDisable`.
     *
     * @param plugin The JavaPlugin instance executing the release.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    fun release(plugin: JavaPlugin) = NMKey.release(plugin, pluginId)

    /**
     * Disables the plugin via the Bukkit PluginManager if the validation failed.
     *
     * This method must be called from the server thread.
     *
     * @param plugin The JavaPlugin instance to disable.
     * @return True if the session is valid, false if the plugin was disabled.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    fun disablePluginIfInvalid(plugin: JavaPlugin): Boolean = valid.also {
        if (!it) plugin.server.pluginManager.disablePlugin(plugin)
    }
}

/**
 * Validates the plugin's license key asynchronously using the Bukkit scheduler.
 *
 * If [Config.autoDisablePlugin] is true, the plugin will automatically
 * be disabled if the license is invalid.
 *
 * ### Self-Cleaning Architecture
 * This method automatically hooks into Bukkit's [org.bukkit.event.server.PluginDisableEvent].
 * You do **not** need to manually call `releaseNmKey()` in your plugin's `onDisable`
 * phase; NMKey will automatically release the license seat and destroy its background
 * HTTP threads to prevent memory leaks during server reloads.
 *
 * @param pluginId The unique ID of the plugin.
 * @param onResult An optional callback receiving the [NMKeySession].
 * @throws IllegalArgumentException If [pluginId] is blank.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.1.0
 */
@Throws(IllegalArgumentException::class)
fun JavaPlugin.nmKeyAsync(pluginId: String, onResult: ((NMKeySession) -> Unit)? = null) {
    val validId = requirePluginId(pluginId)

    server.scheduler.runTaskAsynchronously(
        this,
        Runnable {
            val session = NMKeySession(validId, NMKey.check(this, validId))

            server.scheduler.runTask(
                this,
                Runnable {
                    if (!session.valid && Config.autoDisablePlugin) {
                        server.pluginManager.disablePlugin(this)
                    }
                    onResult?.invoke(session)
                },
            )
        },
    )
}

/**
 * Validates the plugin's license key against the NMKey API synchronously.
 *
 * If [Config.autoDisablePlugin] is true, the plugin will automatically
 * be disabled if the license is invalid.
 *
 * ### Self-Cleaning Architecture
 * This method automatically hooks into Bukkit's [org.bukkit.event.server.PluginDisableEvent].
 * You do **not** need to manually call `releaseNmKey()` in your plugin's `onDisable`
 * phase; NMKey will automatically release the license seat and destroy its background
 * HTTP threads to prevent memory leaks during server reloads.
 *
 * @param pluginId The unique ID of the plugin.
 * @return A [NMKeySession] object containing the validation result.
 * @throws IllegalArgumentException If [pluginId] is blank.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
@Throws(IllegalArgumentException::class)
fun JavaPlugin.nmKey(pluginId: String): NMKeySession {
    val validId = requirePluginId(pluginId)
    return NMKeySession(validId, NMKey.check(this, validId)).also {
        if (!it.valid && Config.autoDisablePlugin) {
            server.pluginManager.disablePlugin(this)
        }
    }
}

/**
 * Releases the license seat associated with this plugin instance on the remote server.
 * This method runs synchronously and is safe to call inside `onDisable()`.
 *
 * @param pluginId The unique ID of the plugin.
 * @throws IllegalArgumentException If [pluginId] is blank.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
@Throws(IllegalArgumentException::class)
fun JavaPlugin.releaseNmKey(pluginId: String) = NMKey.release(this, requirePluginId(pluginId))

/**
 * Validates that the plugin ID is not blank.
 *
 * @param pluginId The ID to check.
 * @return The plugin ID if valid.
 * @author Idan Nehama (GuavaDealer)
 */
private fun requirePluginId(pluginId: String): String = pluginId.also {
    require(it.isNotBlank()) { "NMKey pluginId must not be blank" }
}
