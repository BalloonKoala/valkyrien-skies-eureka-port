package org.valkyrienskies.eureka.util

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.common.config.AddedFeatures

/** What players see when they use something that `added-features.toml` turned off. */
object TurnedOff {
    /** Whether an item of this mod is turned on in added-features.toml. */
    @JvmStatic
    fun itemOn(item: net.minecraft.world.item.Item): Boolean {
        val id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
        if (id.namespace != org.valkyrienskies.eureka.EurekaMod.MOD_ID) return true
        val name = id.path
        return when {
            name == "cannon" -> AddedFeatures.cannon
            name.endsWith("_cannonball") -> AddedFeatures.cannonballs && AddedFeatures.cannon
            name.endsWith("_wireless_button") -> AddedFeatures.wirelessButtons
            name == "detacher" -> AddedFeatures.detacher
            name == "electric_weight" -> AddedFeatures.electricWeight
            else -> true
        }
    }

    @JvmStatic
    fun tell(player: Player?) {
        if (player != null && !player.level().isClientSide) {
            player.sendOverlayMessage(Component.translatable("message.vs_eureka.turned_off"))
        }
    }
}
