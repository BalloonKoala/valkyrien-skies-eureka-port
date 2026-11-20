package org.valkyrienskies.mod.common.config

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.neoforged.fml.config.ModConfig
import org.apache.logging.log4j.LogManager
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

/** Registers `added-features.toml`, its recipe condition, and sends its switches to players. */
object AddedFeaturesSetup {
    private val logger = LogManager.getLogger("AddedFeatures")

    /** Sends the file's switches to everyone playing, after it changed. */
    @JvmStatic
    fun sendToAll(server: net.minecraft.server.MinecraftServer) {
        val payload = AddedFeatures.Sync(AddedFeatures.local())
        for (player in server.playerList.players) {
            if (ServerPlayNetworking.canSend(player, AddedFeatures.Sync.TYPE)) ServerPlayNetworking.send(player, payload)
        }
    }

    fun register() {
        ConfigRegistry.INSTANCE.register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.COMMON, AddedFeatures.SPEC, AddedFeatures.FILE)
        AddedFeatures.registerCondition()

        PayloadTypeRegistry.clientboundConfiguration().register(AddedFeatures.Sync.TYPE, AddedFeatures.Sync.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(AddedFeatures.Sync.TYPE, AddedFeatures.Sync.CODEC)

        // Before a player is in the world, so the creative tab and everything else is set up with the server's switches
        ServerConfigurationConnectionEvents.CONFIGURE.register { handler, _ ->
            if (ServerConfigurationNetworking.canSend(handler, AddedFeatures.Sync.TYPE)) {
                ServerConfigurationNetworking.send(handler, AddedFeatures.Sync(AddedFeatures.local()))
            }
        }

        val onChange = { config: ModConfig ->
            if (config.spec === AddedFeatures.SPEC) {
                val off = AddedFeatures.local().filterValues { !it }.keys
                logger.info("Added features turned off: {}", if (off.isEmpty()) "none" else off.joinToString())
                val server = ValkyrienSkiesMod.currentServer
                server?.execute { sendToAll(server) }
            }
        }
        ModConfigEvents.loading(ValkyrienSkiesMod.MOD_ID).register(onChange)
        ModConfigEvents.reloading(ValkyrienSkiesMod.MOD_ID).register(onChange)
    }
}
