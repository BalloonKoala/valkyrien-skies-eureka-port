package org.valkyrienskies.eureka.block

import org.valkyrienskies.mod.common.config.AddedFeatures
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.redstone.Orientation

/**
 * Switches between light and heavy, like a copper bulb: every redstone pulse flips it. Off it weighs as much as an oak
 * log, on as much as a netherite block (see EurekaWeights).
 */
class ElectricWeightBlock(properties: BlockBehaviour.Properties) : Block(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(POWERED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LIT, POWERED)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        if (oldState.block != state.block && level is ServerLevel) flipOnPulse(state, level, pos)
    }

    override fun neighborChanged(
        state: BlockState, level: Level, pos: BlockPos, block: Block, orientation: Orientation?, movedByPiston: Boolean
    ) {
        if (level is ServerLevel) flipOnPulse(state, level, pos)
    }

    private fun flipOnPulse(state: BlockState, level: ServerLevel, pos: BlockPos) {
        if (!AddedFeatures.electricWeight) return
        val powered = level.hasNeighborSignal(pos)
        if (powered == state.getValue(POWERED)) return
        var next = state.setValue(POWERED, powered)
        if (powered) {
            next = next.cycle(LIT)
            level.playSound(
                null, pos, if (next.getValue(LIT)) SoundEvents.COPPER_BULB_TURN_ON else SoundEvents.COPPER_BULB_TURN_OFF,
                SoundSource.BLOCKS, 1.0f, 0.7f
            )
        }
        level.setBlock(pos, next, UPDATE_ALL)
    }

    companion object {
        val LIT: BooleanProperty = BlockStateProperties.LIT
        val POWERED: BooleanProperty = BlockStateProperties.POWERED
    }
}
