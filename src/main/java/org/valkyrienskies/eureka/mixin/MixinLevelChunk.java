package org.valkyrienskies.eureka.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.eureka.block.BlockRemovalListener;

/**
 * Keeps Block.onRemove's old timing for Eureka's blocks: once for every state change, whichever update flags were
 * used, which is what pairs each removal with the onPlace that follows it.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {

    @Shadow
    @Final
    Level level;

    @WrapOperation(
        method = "setBlockState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState notifyRemoval(final LevelChunkSection section, final int x, final int y, final int z,
                                     final BlockState newState, final Operation<BlockState> original,
                                     @Local(argsOnly = true) final BlockPos pos,
                                     @Local(argsOnly = true) final int flags) {
        final BlockState oldState = original.call(section, x, y, z, newState);
        if (oldState != newState && !level.isClientSide() && oldState.getBlock() instanceof final BlockRemovalListener listener) {
            listener.onBlockRemoved(oldState, level, pos, newState, (flags & 64) != 0);
        }
        return oldState;
    }
}
