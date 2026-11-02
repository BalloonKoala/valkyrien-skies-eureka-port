package org.valkyrienskies.eureka.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.DispenserBlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.eureka.EurekaBlockEntities

class CannonBlockEntity(pos: BlockPos, state: BlockState) :
    DispenserBlockEntity(EurekaBlockEntities.CANNON.get(), pos, state) {

    override fun getDefaultName(): Component = Component.translatable("container.vs_eureka.cannon")

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = stack.`is`(Items.TNT)
}
