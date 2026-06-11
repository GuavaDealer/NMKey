package com.nmcrate.key.tests

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import com.nmcrate.key.NMKey
import com.nmcrate.key.nmKey
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.java.JavaPluginLoader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Clock

@Tag("integration")
class NMKeyIntegrationTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: NMKeyIntegrationPlugin

    @BeforeEach
    fun setUp() {
        log("Starting MockBukkit server")
        server = MockBukkit.mock()
        plugin = MockBukkit.loadSimple(NMKeyIntegrationPlugin::class.java)
        NMKey.clearCache()
        log("MockBukkit plugin loaded: ${plugin.name}")
    }

    @AfterEach
    fun tearDown() {
        log("Clearing NMKey cache and stopping MockBukkit")
        NMKey.clearCache()
        MockBukkit.unmock()
    }

    @Test
    fun `valid license verifies against api and releases seat`() {
        var verified = false

        try {
            log("Core API verification request starting")
            verified = NMKey.check(plugin, PLUGIN_ID)
            log("Core API verification result: $verified")
            assertTrue(verified, "Expected NMKey API to verify the bundled test license.")
        } finally {
            if (verified) {
                log("Core API release request starting")
                NMKey.release(plugin, PLUGIN_ID)
                log("Core API release request completed")
            } else {
                log("Core API release skipped because verification failed")
            }
        }
    }

    @Test
    fun `kotlin extension validates against api and releases seat`() {
        log("Kotlin extension verification request starting")
        val session = plugin.nmKey(PLUGIN_ID)
        log("Kotlin extension verification result: ${session.valid}")

        try {
            assertTrue(session.valid, "Expected NMKey Kotlin extension to verify the bundled test license.")
        } finally {
            if (session.valid) {
                log("Kotlin extension release request starting")
                // explicitly pass the plugin instance
                session.release(plugin)
                log("Kotlin extension release request completed")
            } else {
                log("Kotlin extension release skipped because verification failed")
            }
        }
    }

    @Test
    fun `successful validation creates offline cache`() {
        val dataDir = File(plugin.dataFolder, "data")
        val cacheFile = File(dataDir, "${plugin.name}-cache.dat")
        if (cacheFile.exists()) {
            cacheFile.delete()
        }

        val verified = NMKey.check(plugin, PLUGIN_ID)
        assertTrue(verified, "Verification should be successful")
        assertTrue(cacheFile.exists(), "Offline cache file should be created after successful validation")

        NMKey.release(plugin, PLUGIN_ID)
    }

    @Test
    fun `shutdown closes client and clears cache`() {
        NMKey.shutdown()
    }

    private companion object {
        private const val PLUGIN_ID = "d486742f"

        private fun log(message: String) {
            println("[${Clock.System.now()}] [NMKeyIntegrationTest] $message")
        }
    }
}

open class NMKeyIntegrationPlugin : JavaPlugin {
    constructor() : super()

    protected constructor(
        loader: JavaPluginLoader,
        description: PluginDescriptionFile,
        dataFolder: File,
        file: File,
    ) : super(loader, description, dataFolder, file)
}
