package org.valkyrienskies.mod.fabric.mixin.compat.sodium;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.SodiumCompat;

@Mixin(value = SodiumWorldRenderer.class, remap = false, priority = 1100)
public abstract class MixinSodiumWorldRenderer {
    @Shadow
    private RenderSectionManager renderSectionManager;

    @Shadow
    private UniformBufferManager uniformBufferManager;

    @Shadow
    private boolean useTranslucencySorting;

    @Inject(method = "renderLayer", at = @At("TAIL"))
    private void afterRenderLayer(final ChunkRenderMatrices matrices, final TerrainRenderPass pass, final double x,
        final double y, final double z, final FogParameters fogParameters, final GpuSampler terrainSampler,
        final RenderPass renderPass, final @Nullable OitStage stage, final CallbackInfo ci) {
        SodiumCompat.vsRenderLayer(renderSectionManager, uniformBufferManager, matrices, pass, x, y, z, fogParameters,
            terrainSampler, renderPass, stage, useTranslucencySorting);
    }
}
