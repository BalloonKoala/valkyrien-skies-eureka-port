package org.valkyrienskies.mod.mixin.accessors.world.level.chunk;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkAccess.class)
public interface ChunkAccessAccessor {
    @Mutable
    @Accessor("blendingData")
    void vs$setBlendingData(BlendingData blendingData);
}
