package org.valkyrienskies.mod.client.config

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import org.valkyrienskies.mod.common.config.AddedFeatures

/** Takes the server's switches while connected, and goes back to the local file after. */
object AddedFeaturesClient {
    fun register() {
        AddedFeatures.setClientThread(Thread.currentThread())
        ClientConfigurationNetworking.registerGlobalReceiver(AddedFeatures.Sync.TYPE) { payload, _ ->
            AddedFeatures.setFromServer(payload.switches)
        }
        ClientPlayNetworking.registerGlobalReceiver(AddedFeatures.Sync.TYPE) { payload, _ ->
            AddedFeatures.setFromServer(payload.switches)
        }
        ClientConfigurationConnectionEvents.DISCONNECT.register { _, _ -> AddedFeatures.setFromServer(null) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> AddedFeatures.setFromServer(null) }
    }
}
