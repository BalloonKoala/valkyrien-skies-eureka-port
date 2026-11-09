package org.valkyrienskies.mod.mixin.feature.placed_blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.PlacedBlocks;

/** Keeps the record of placed blocks up to date with every block that changes on the server. */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkPlaced {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void vs$trackPlaced(final BlockPos pos, final BlockState state, final int flags,
        final CallbackInfoReturnable<BlockState> cir) {
        final BlockState old = cir.getReturnValue();
        final LevelChunk self = (LevelChunk) (Object) this;
        if (old != null && self.getLevel() instanceof ServerLevel) {
            PlacedBlocks.onBlockChanged(self, pos, old, state);
        }
    }
}
