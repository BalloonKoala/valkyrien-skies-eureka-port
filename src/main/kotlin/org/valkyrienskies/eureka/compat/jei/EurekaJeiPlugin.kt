package org.valkyrienskies.eureka.compat.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.valkyrienskies.eureka.EurekaItems
import org.valkyrienskies.eureka.EurekaMod
import org.valkyrienskies.eureka.util.TurnedOff

/** Hides the items `added-features.toml` turned off from JEI's item list, like the creative tab. Only loaded with JEI. */
class EurekaJeiPlugin : IModPlugin {
    override fun getPluginUid(): Identifier = Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "added_features")

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        val off = EurekaItems.ITEMS.map { it.get() }.filterNot { TurnedOff.itemOn(it) }.map { ItemStack(it) }
        if (off.isNotEmpty()) jeiRuntime.ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, off)
    }
}
