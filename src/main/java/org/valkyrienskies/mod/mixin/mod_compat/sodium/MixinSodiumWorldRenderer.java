package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class MixinSodiumWorldRenderer {

    @Shadow
    private ClientLevel level;

    @Shadow
    private RenderSectionManager renderSectionManager;

    @Shadow
    private void extractBlockEntity(final BlockEntity blockEntity, final PoseStack poseStack, final Camera camera,
        final float tickDelta, final Long2ObjectMap<SortedSet<BlockDestructionProgress>> progression,
        final LevelRenderState levelRenderState, final boolean global) {
        throw new AssertionError();
    }

    @Unique
    private void vs$forEachShipBlockEntity(final Consumer<BlockEntity> consumer) {
        for (final SortedRenderLists renderLists : ((RenderSectionManagerDuck) this.renderSectionManager).vs_getShipRenderLists().values()) {
            for (final Iterator<ChunkRenderList> lists = renderLists.iterator(); lists.hasNext(); ) {
                final ChunkRenderList renderList = lists.next();
                final RenderRegion region = renderList.getRegion();
                final ByteIterator sections = renderList.sectionsWithEntitiesIterator();
                if (sections == null) {
                    continue;
                }
                while (sections.hasNext()) {
                    final BlockEntity[] blockEntities = region.getCulledBlockEntities(sections.nextByteAsInt());
                    if (blockEntities != null) {
                        for (final BlockEntity blockEntity : blockEntities) {
                            consumer.accept(blockEntity);
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "extractBlockEntities", at = @At("TAIL"))
    private void extractShipBlockEntities(final Camera camera, final float tickDelta,
        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> progression, final LevelRenderState levelRenderState,
        final CallbackInfo ci) {
        final PoseStack stack = new PoseStack();
        vs$forEachShipBlockEntity(blockEntity ->
            extractBlockEntity(blockEntity, stack, camera, tickDelta, progression, levelRenderState, false));
    }

    @Inject(method = "iterateVisibleBlockEntities", at = @At("TAIL"))
    private void iterateShipBlockEntities(final Consumer<BlockEntity> blockEntityConsumer, final CallbackInfo ci) {
        vs$forEachShipBlockEntity(blockEntityConsumer);
    }

    /**
     * Fix entities in ships not rendering when Sodium is installed
     */
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private <T extends Entity, S extends EntityRenderState> void isEntityVisible(final EntityRenderer<T, S> renderer,
        final T entity, final float partialTicks, final CallbackInfoReturnable<Boolean> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(level, entity.position())) {
            cir.setReturnValue(true);
        }
    }
}
