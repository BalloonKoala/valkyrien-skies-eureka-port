package org.valkyrienskies.mod.mixinducks.mod_compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;

public interface DefaultChunkRendererDuck {
    /**
     * Same as prepare, but keeps the passes that already have something to draw.
     */
    void vs$prepareAlongside(ChunkRenderListIterable renderLists, CameraTransform camera, boolean indexedRendering);
}
