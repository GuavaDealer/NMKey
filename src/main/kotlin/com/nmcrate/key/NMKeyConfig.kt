package com.nmcrate.key

/**
 * Global configuration settings for NMKey behavior.
 * Modify these values before calling `NMKey.check()` or `nmKeyAsync()`.

 * @author Idan Nehama (GuavaDealer)
 * @since 1.1.0
 */
object Config {
    /** Whether to utilize the AES-256 encrypted offline cache. */
    @JvmStatic
    var useOfflineCache: Boolean = true

    /** The directory path relative to the plugin's data folder to store the cache. */
    @JvmStatic
    var cacheDirectoryPath: String = "data"

    /** The file name format for the cache. Use %s to substitute the plugin's name. */
    @JvmStatic
    var cacheFileNameFormat: String = "%s-cache.dat"

    /** Automatically disable the plugin if the license validation fails. */
    @JvmStatic
    var autoDisablePlugin: Boolean = true

    /** Automatically release the license seat when Bukkit fires the PluginDisableEvent. */
    @JvmStatic
    var autoReleaseOnDisable: Boolean = true

    /** The amount of time in hours a license remains valid offline after a failed heartbeat. */
    @JvmStatic
    var gracePeriodHours: Long = 48L
}
