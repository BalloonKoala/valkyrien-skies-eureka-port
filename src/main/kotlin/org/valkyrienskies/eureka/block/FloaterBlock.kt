package org.valkyrienskies.eureka.block

import net.minecraft.world.level.redstone.Orientation
import org.valkyrienskies.eureka.eurekaBlockProperties
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER
import net.minecraft.world.level.material.MapColor
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.eureka.ship.EurekaShipControl
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipObjectManagingPos

class FloaterBlock : Block(
    eurekaBlockProperties("floater").mapColor(MapColor.WOOD)
        .sound(SoundType.WOOL).strength(1.0f, 2.0f)
), BlockRemovalListener {
    init {
        registerDefaultState(defaultBlockState().setValue(POWER, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(POWER)
    }

    @OptIn(GameTickOnly::class)
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)

        if (level.isClientSide) return
        level as ServerLevel

        val floaterPower = 15 - state.getValue(POWER)

        val ship = level.getLoadedShipManagingPos(pos) ?: level.getShipManagingPos(pos) ?: return
        EurekaShipControl.deferUntilLoaded(ship) { it.floaters += floaterPower }
    }

    @OptIn(GameTickOnly::class, VsBeta::class)
    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        orientation: Orientation?,
        isMoving: Boolean
    ) {
        super.neighborChanged(state, level, pos, block, orientation, isMoving)

        if (level as? ServerLevel == null) return

        val signal = level.getBestNeighborSignal(pos)
        val currentPower = state.getValue(POWER)

        val ship = level.getLoadedShipManagingPos(pos) ?: level.getShipManagingPos(pos)
        if(ship != null) EurekaShipControl.deferUntilLoaded(ship) { it.floaters += (currentPower - signal) }

        level.setBlock(pos, state.setValue(POWER, signal), 2)
    }

    @OptIn(GameTickOnly::class, VsBeta::class)
    override fun onBlockRemoved(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {

        if (level.isClientSide) return
        level as ServerLevel

        val floaterPower = 15 - state.getValue(POWER)

        val ship = level.getLoadedShipManagingPos(pos) ?: level.getShipManagingPos(pos) ?: return
        EurekaShipControl.deferUntilLoaded(ship) { it.floaters -= floaterPower }
    }
}
