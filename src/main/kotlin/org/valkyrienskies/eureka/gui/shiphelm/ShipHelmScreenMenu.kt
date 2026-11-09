package org.valkyrienskies.eureka.gui.shiphelm

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.EurekaScreens
import org.valkyrienskies.eureka.assembly.AssemblyPreview
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity
import org.valkyrienskies.eureka.wiring.ShipWiring

class ShipHelmScreenMenu(syncId: Int, playerInv: Inventory, private val blockEntity: ShipHelmBlockEntity?) :
    AbstractContainerMenu(EurekaScreens.SHIP_HELM.get(), syncId) {

    constructor(syncId: Int, playerInv: Inventory) : this(syncId, playerInv, null)

    // TODO this isn't synced...
    val aligning = blockEntity?.aligning == true
    val assembled = blockEntity?.assembled == true

    override fun stillValid(player: Player): Boolean = true

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (blockEntity == null) return false

        // Shows what would become the ship first, the Assemble button under that preview assembles it
        if (id == 0 && !assembled && player is ServerPlayer) {
            AssemblyPreview.sendTo(player, blockEntity)
            return true
        }

        if (id == 1 && assembled && !player.level().isClientSide) {
            blockEntity.align()
            return true
        }

        if (id == 3 && assembled && !player.level().isClientSide && EurekaConfig.SERVER.allowDisassembly) {
            blockEntity.disassemble()
            return true
        }

        if (id == WIRING_BUTTON && blockEntity.assembled && player is ServerPlayer) {
            ShipWiring.sendTo(player, blockEntity.blockPos)
            return true
        }

        return super.clickMenuButton(player, id)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        // Do nothing
        return ItemStack.EMPTY
    }

    companion object {
        const val WIRING_BUTTON = 4

        val factory: (syncId: Int, playerInv: Inventory) -> ShipHelmScreenMenu = ::ShipHelmScreenMenu
    }
}
