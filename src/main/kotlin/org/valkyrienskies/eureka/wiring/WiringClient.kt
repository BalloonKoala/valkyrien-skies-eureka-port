package org.valkyrienskies.eureka.wiring

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import org.valkyrienskies.eureka.gui.wiring.ShipWiringScreen
import org.valkyrienskies.mod.common.getShipManagingPos

object WiringClient {
    @JvmStatic
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(WiringDataPayload.TYPE) { payload, context ->
            context.client().execute { onData(context.client(), payload) }
        }
    }

    private fun onData(minecraft: Minecraft, payload: WiringDataPayload) {
        val screen = minecraft.gui.screen()
        if (screen is ShipWiringScreen) {
            screen.update(payload)
        } else {
            minecraft.player?.closeContainer()
            minecraft.gui.setScreen(ShipWiringScreen(payload))
        }
    }

    /** Called for every key that goes down during play. The server knows what's wired to what. */
    @JvmStatic
    fun onKeyPressed(key: String) {
        val minecraft = Minecraft.getInstance()
        val vehicle = minecraft.player?.vehicle ?: return
        if (minecraft.level?.getShipManagingPos(vehicle.blockPosition()) != null) {
            ClientPlayNetworking.send(WiringKeyPayload(key))
        }
    }
}
