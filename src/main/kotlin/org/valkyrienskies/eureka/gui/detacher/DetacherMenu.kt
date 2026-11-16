package org.valkyrienskies.eureka.gui.detacher

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import org.valkyrienskies.eureka.EurekaScreens
import org.valkyrienskies.eureka.blockentity.DetacherBlockEntity

/** The detacher's settings. The screen sets the push by pressing menu button number "push in half blocks". */
class DetacherMenu(syncId: Int, playerInv: Inventory, private val detacher: DetacherBlockEntity?) :
    AbstractContainerMenu(EurekaScreens.DETACHER.get(), syncId) {

    constructor(syncId: Int, playerInv: Inventory) : this(syncId, playerInv, null)

    private val data: ContainerData = detacher?.let {
        object : ContainerData {
            override fun get(index: Int): Int = it.pushHalfBlocks
            override fun set(index: Int, value: Int) {
                it.pushHalfBlocks = value
            }
            override fun getCount(): Int = 1
        }
    } ?: SimpleContainerData(1)

    val pushHalfBlocks: Int get() = data.get(0)

    init {
        addDataSlots(data)
    }

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (id !in DetacherBlockEntity.MIN_PUSH_HALF_BLOCKS..DetacherBlockEntity.MAX_PUSH_HALF_BLOCKS) return false
        detacher?.pushHalfBlocks = id
        return true
    }

    override fun stillValid(player: Player): Boolean = detacher == null || Container.stillValidBlockEntity(detacher, player)

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    companion object {
        val factory: (syncId: Int, playerInv: Inventory) -> DetacherMenu = ::DetacherMenu
    }
}
