package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRenderer;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.mixinducks.client.render.IVSViewAreaMethods;

/**
 * The purpose of this mixin is to allow {@link ViewArea} to render ship chunks.
 */
@Mixin(ViewArea.class)
public class MixinViewAreaVanilla implements IVSViewAreaMethods {

    // Maps section position to its render section
    @Unique
    private final Long2ObjectMap<SectionRenderDispatcher.RenderSection> vs$shipRenderChunks =
        new Long2ObjectOpenHashMap<>();
    // This creates render chunks
    @Unique
    private SectionRenderDispatcher vs$chunkBuilder;
    @Unique
    private int vs$minSectionY;
    @Unique
    private int vs$maxSectionY;

    /**
     * This mixin stores the [chunkBuilder] object from the constructor. It is used to create new render chunks.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(final SectionRenderDispatcher chunkBuilder, final int minY, final int maxY,
        final int minSectionY, final int maxSectionY, final int renderDistance,
        final SectionOcclusionGraph sectionOcclusionGraph, final CallbackInfo callbackInfo) {

        this.vs$chunkBuilder = chunkBuilder;
        this.vs$minSectionY = minSectionY;
        this.vs$maxSectionY = maxSectionY;
    }

    /**
     * Ship sections only exist once something asks for them, which also makes this the place that creates them.
     */
    @Unique
    private SectionRenderDispatcher.RenderSection vs$getShipSection(final long sectionPos) {
        final ClientLevel level = Minecraft.getInstance().level;
        final int y = SectionPos.y(sectionPos);
        if (level == null || y < vs$minSectionY || y > vs$maxSectionY) {
            return null;
        }

        final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, SectionPos.x(sectionPos), SectionPos.z(sectionPos));
        if (ship == null || ShipRendererKt.getShipRenderer(ship) != ShipRenderer.VANILLA) {
            return null;
        }
        return vs$shipRenderChunks.computeIfAbsent(sectionPos, k -> vs$chunkBuilder.new RenderSection(0, sectionPos));
    }

    /**
     * This mixin allows {@link ViewArea} to return the render chunks for ships.
     */
    @Inject(method = "getRenderSection(J)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;", at = @At("HEAD"), cancellable = true)
    private void preGetRenderedChunk(final long sectionPos,
        final CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> callbackInfoReturnable) {
        final SectionRenderDispatcher.RenderSection section = vs$getShipSection(sectionPos);
        if (section != null) {
            callbackInfoReturnable.setReturnValue(section);
        }
    }

    @Inject(method = "getRenderSectionAt", at = @At("HEAD"), cancellable = true)
    private void preGetRenderSectionAt(final BlockPos pos,
        final CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> callbackInfoReturnable) {
        final SectionRenderDispatcher.RenderSection section = vs$getShipSection(SectionPos.asLong(pos));
        if (section != null) {
            callbackInfoReturnable.setReturnValue(section);
        }
    }

    @Override
    public void unloadChunk(final int chunkX, final int chunkZ) {
        for (int y = vs$minSectionY; y <= vs$maxSectionY; y++) {
            final SectionRenderDispatcher.RenderSection section =
                vs$shipRenderChunks.remove(SectionPos.asLong(chunkX, y, chunkZ));
            if (section != null) {
                section.reset();
            }
        }
    }

    /**
     * Clear VS ship render chunks so that we don't leak memory
     */
    @Inject(method = "releaseAllBuffers", at = @At("HEAD"))
    private void postReleaseAllBuffers(final CallbackInfo ci) {
        for (final SectionRenderDispatcher.RenderSection renderChunk : vs$shipRenderChunks.values()) {
            renderChunk.reset();
        }
        vs$shipRenderChunks.clear();
    }
}
