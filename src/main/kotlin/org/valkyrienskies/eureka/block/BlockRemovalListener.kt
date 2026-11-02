package org.valkyrienskies.eureka.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Minecraft dropped Block.onRemove, and the closest replacement only fires for some block changes.
 * Eureka counts balloons, floaters and anchors per ship, so every onPlace has to stay paired with a
 * removal. MixinLevelChunk calls this at the points where onRemove used to run.
 */
interface BlockRemovalListener {
    fun onBlockRemoved(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean)
}
