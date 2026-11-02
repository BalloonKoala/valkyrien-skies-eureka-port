package org.valkyrienskies.eureka.block

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.InsideBlockEffectApplier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.phys.BlockHitResult
import org.valkyrienskies.eureka.blockentity.WirelessButtonBlockEntity

/**
 * A button nobody can press by hand. It's wired up from the helm, and a key press from the seat it's wired to pulses it.
 */
class WirelessButtonBlock(type: BlockSetType, properties: BlockBehaviour.Properties) :
    ButtonBlock(type, PULSE_TICKS, properties), EntityBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = WirelessButtonBlockEntity(pos, state)

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult =
        InteractionResult.PASS

    // Arrows press wooden buttons, not this one
    override fun entityInside(
        state: BlockState, level: Level, pos: BlockPos, entity: Entity, effectApplier: InsideBlockEffectApplier, isPrecise: Boolean
    ) {
    }

    fun pulse(state: BlockState, level: Level, pos: BlockPos) {
        if (!state.getValue(POWERED)) press(state, level, pos, null)
    }

    private companion object {
        const val PULSE_TICKS = 2
    }
}
