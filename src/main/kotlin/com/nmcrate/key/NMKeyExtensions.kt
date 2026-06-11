package com.nmcrate.key

import org.bukkit.plugin.java.JavaPlugin

/**
 * Represents the result of an NMKey validation attempt and provides lifecycle management.
 *
 * A session encapsulates the state of a license check, allowing the developer to
 * react to invalid keys or release the license seat when the plugin is disabled.
 *
 * @param plugin The [JavaPlugin] instance associated with this session.
 * @param pluginId The ID used for the validation request.
 * @param valid Whether the key validation was successful.
 *
 * @author Idan Nehama (GuavaDealer)
 * @author QrackyDev (Qracky)
 * @since 1.0.0
 */
class NMKeySession internal constructor(
    private val plugin: JavaPlugin,
    val pluginId: String,
    val valid: Boolean,
) {
    /**
     * Releases the active license seat for this plugin on the remote server.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    fun release(): Unit = NMKey.release(plugin, pluginId)

    /**
     * Disables the plugin via the Bukkit PluginManager if the validation failed.
     *
     * This method must be called from the server thread. Prefer
     * [disablePluginTaskIfInvalid] when validation can happen asynchronously.
     *
     * @return True if the session is valid, false if the plugin was disabled.
     * @see disablePluginTaskIfInvalid
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    fun disablePluginIfInvalid(): Boolean {
        if (!valid) {
            plugin.server.pluginManager.disablePlugin(plugin)
        }
        return valid
    }

    /**
     * Schedules a server-thread task to disable the plugin if validation failed.
     *
     * This is the recommended helper for most use cases, as license checks
     * are commonly performed from asynchronous startup or periodic tasks.
     *
     * @return True if the session is valid, false if a disable task was scheduled.
     * @see disablePluginIfInvalid
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    fun disablePluginTaskIfInvalid(): Boolean {
        if (!valid) {
            plugin.server.scheduler.runTask(
                plugin,
                Runnable {
                    if (plugin.isEnabled) {
                        plugin.server.pluginManager.disablePlugin(plugin)
                    }
                },
            )
        }
        return valid
    }
}

/**
 * Validates the plugin's license key against the NMKey API.
 *
 * @param pluginId The unique ID of the plugin.
 * @return A [NMKeySession] object containing the validation result and lifecycle management methods.
 * @throws IllegalArgumentException If [pluginId] is blank.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
@Throws(Throwable::class)
fun JavaPlugin.nmKey(pluginId: String): NMKeySession {
    val validPluginId = requirePluginId(pluginId)

    return NMKeySession(
        plugin = this,
        pluginId = validPluginId,
        valid = NMKey.check(this, validPluginId),
    )
}

/**
 * Releases the license seat associated with this plugin instance on the remote server.
 *
 * @param pluginId The unique ID of the plugin.
 * @throws IllegalArgumentException If [pluginId] is blank.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
fun JavaPlugin.releaseNmKey(pluginId: String) {
    NMKey.release(this, requirePluginId(pluginId))
}

private fun requirePluginId(pluginId: String): String {
    require(pluginId.isNotBlank()) { "NMKey pluginId must not be blank" }
    return pluginId
}
