package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.client.render.ChunkSectionsToRenderDuck;

/**
 * Terrain uses one model view matrix per batch of draws, so every ship is its own batch, drawn right after the world.
 * All terrain drawing goes through renderLayers, which covers solid, translucent and order independent transparency.
 */
@Mixin(ChunkSectionsToRender.class)
public abstract class MixinChunkSectionsToRender implements ChunkSectionsToRenderDuck {
    @Unique
    private List<ChunkSectionsToRender> vs$shipRenders = List.of();

    @Shadow
    private void renderLayers(final ChunkSectionLayer[] layers, final GpuSampler sampler, final RenderPass renderPass,
        final GpuTextureView atlas, final GpuTextureView lightmap, final @Nullable RenderPipeline renderPipelineOverride,
        final @Nullable RenderPipeline renderPipelineOverrideMultidraw) {
        throw new AssertionError();
    }

    @Override
    public void vs$setShipRenders(final List<ChunkSectionsToRender> shipRenders) {
        this.vs$shipRenders = shipRenders;
    }

    @Inject(method = "renderLayers", at = @At("TAIL"))
    private void renderShips(final ChunkSectionLayer[] layers, final GpuSampler sampler, final RenderPass renderPass,
        final GpuTextureView atlas, final GpuTextureView lightmap, final @Nullable RenderPipeline renderPipelineOverride,
        final @Nullable RenderPipeline renderPipelineOverrideMultidraw, final CallbackInfo ci) {
        for (final ChunkSectionsToRender ship : vs$shipRenders) {
            ((MixinChunkSectionsToRender) (Object) ship).renderLayers(layers, sampler, renderPass, atlas, lightmap,
                renderPipelineOverride, renderPipelineOverrideMultidraw);
        }
    }
}
