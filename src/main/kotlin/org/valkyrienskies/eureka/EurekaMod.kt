package org.valkyrienskies.eureka

import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.eureka.assembly.AssemblyPreview
import org.valkyrienskies.eureka.assembly.AssemblyPreviewClient
import org.valkyrienskies.eureka.ship.EurekaShipControl
import org.valkyrienskies.eureka.wiring.ShipWiring
import org.valkyrienskies.eureka.wiring.WiringClient
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

object EurekaMod {
    const val MOD_ID = "vs_eureka"

    @OptIn(VsBeta::class)
    @JvmStatic
    fun init() {
        EurekaBlocks.register()
        EurekaBlockEntities.register()
        EurekaItems.register()
        EurekaScreens.register()
        EurekaEntities.register()
        EurekaWeights.register()
        ShipWiring.register()
        AssemblyPreview.register()
        org.valkyrienskies.eureka.block.ShipHelmBlock.registerCrouchToOpen()
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            org.valkyrienskies.eureka.command.BuildCommand.register(dispatcher)
        }
        ValkyrienSkiesMod.vsCore.registerAttachment(EurekaShipControl::class.java)
    }

    @JvmStatic
    fun initClient() {
        EurekaClientScreens.register()
        WiringClient.register()
        AssemblyPreviewClient.register()
    }
}
