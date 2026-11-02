package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.DefaultChunkRendererDuck;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer implements DefaultChunkRendererDuck {
    @Shadow
    @Final
    private boolean[] shouldDraw;

    @Shadow
    public abstract void prepare(ChunkRenderListIterable renderLists, CameraTransform cameraTransform, boolean indexedRenderingEnabled);

    @Override
    public void vs$prepareAlongside(final ChunkRenderListIterable renderLists, final CameraTransform camera,
        final boolean indexedRendering) {
        final boolean[] previous = shouldDraw.clone();
        prepare(renderLists, camera, indexedRendering);
        for (int i = 0; i < shouldDraw.length; i++) {
            shouldDraw[i] |= previous[i];
        }
    }
}
