package org.valkyrienskies.eureka

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.Identifier
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import org.valkyrienskies.eureka.item.CannonballItem
import org.valkyrienskies.eureka.registry.DeferredRegister
import org.valkyrienskies.eureka.registry.RegistrySupplier

@Suppress("unused")
object EurekaItems {
    internal val ITEMS = DeferredRegister.create(EurekaMod.MOD_ID, Registries.ITEM)
    val TAB: ResourceKey<CreativeModeTab> =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "eureka_tab"))

    lateinit var SMALL_CANNONBALL: RegistrySupplier<CannonballItem>
    lateinit var MEDIUM_CANNONBALL: RegistrySupplier<CannonballItem>
    lateinit var LARGE_CANNONBALL: RegistrySupplier<CannonballItem>

    private fun cannonball(name: String, size: CannonballItem.Size) =
        ITEMS.register(name) { CannonballItem(eurekaItemProperties(name), size) }

    fun register() {
        EurekaBlocks.registerItems(ITEMS)
        // After the blocks, so they come after the cannon in the creative tab
        SMALL_CANNONBALL = cannonball("small_cannonball", CannonballItem.Size.SMALL)
        MEDIUM_CANNONBALL = cannonball("medium_cannonball", CannonballItem.Size.MEDIUM)
        LARGE_CANNONBALL = cannonball("large_cannonball", CannonballItem.Size.LARGE)
        ITEMS.applyAll()
    }

    private infix fun Item.byName(name: String) = ITEMS.register(name) { this }
}
