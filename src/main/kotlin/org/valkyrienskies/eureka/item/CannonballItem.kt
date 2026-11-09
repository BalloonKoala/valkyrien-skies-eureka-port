package org.valkyrienskies.eureka.item

import net.minecraft.world.item.Item
import org.valkyrienskies.eureka.EurekaConfig

/** Ammunition for cannons. Each size explodes twice as hard as the one before it, see the cannonball settings. */
class CannonballItem(properties: Properties, val size: Size) : Item(properties) {
    enum class Size {
        SMALL, MEDIUM, LARGE;

        val explosionPower: Float
            get() = when (this) {
                SMALL -> EurekaConfig.SERVER.smallCannonballPower
                MEDIUM -> EurekaConfig.SERVER.mediumCannonballPower
                LARGE -> EurekaConfig.SERVER.largeCannonballPower
            }.toFloat().coerceIn(0f, MAX_POWER)
    }

    private companion object {
        // Far past this an explosion takes the server down with it
        const val MAX_POWER = 30f
    }
}
