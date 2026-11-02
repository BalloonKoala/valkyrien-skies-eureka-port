package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = UniformBufferManager.class, remap = false)
public interface UniformBufferManagerAccessor {
    @Accessor("uniformData")
    void setUniformData(GpuBufferSlice uniformData);

    @Accessor("hasUpdatedThisFrame")
    void setHasUpdatedThisFrame(boolean hasUpdatedThisFrame);
}
