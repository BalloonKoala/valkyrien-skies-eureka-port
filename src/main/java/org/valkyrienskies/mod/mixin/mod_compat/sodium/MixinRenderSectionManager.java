package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadResourceBudget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.SodiumCompat;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

/**
 * Hi! Not many people read Valkyrien Skies' code, and even fewer will read this particular file. If you're
 * here because you're contributing to VS, thank you so much! This is complex stuff, and your work is appreciated by
 * all of our users.
 * <p>
 * If you're here because you develop a competitor mod, we can't stop you from using this code - but at least have
 * the decency to give credit to us, the original authors, and abide by the terms of our open source license. Don't
 * pretend that you wrote this code. That's not cool.
 *
 * @author Rubydesic
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager implements RenderSectionManagerDuck {

    @Unique
    private final WeakHashMap<ClientShip, SortedRenderLists> shipRenderLists = new WeakHashMap<>();

    /**
     * Sodium culls on another thread and only ever queues sections close to the camera, so ship sections get their own
     * queue.
     */
    @Unique
    private final List<RenderSection> vs$shipTasks = new ArrayList<>();

    @Override
    public WeakHashMap<ClientShip, SortedRenderLists> vs_getShipRenderLists() {
        return shipRenderLists;
    }

    @Shadow
    @Final
    private ClientLevel level;

    @Shadow
    private SortedRenderLists renderLists;

    @Shadow
    @Final
    private RenderRegionManager regions;

    @Shadow
    private int frame;

    @Shadow
    protected abstract RenderSection getRenderSection(int x, int y, int z);

    @Shadow
    protected abstract void submitSectionTask(ChunkJobCollector collector, RenderSection section,
        UploadResourceBudget uploadBudget);

    @Inject(at = @At("TAIL"), method = "finalizeRenderLists")
    private void afterIterateChunks(final Camera camera, final Viewport viewport, final FogParameters fogParameters,
        final boolean updateChunksImmediately, final CallbackInfo ci) {
        shipRenderLists.clear();
        vs$shipTasks.clear();

        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips()) {
            final VisibleChunkCollector collector = new VisibleChunkCollector(regions, frame);

            ship.getActiveChunksSet().forEach((x, z) -> {
                final LevelChunk levelChunk = level.getChunk(x, z);
                for (int y = level.getMinSectionY(); y <= level.getMaxSectionY(); y++) {
                    // If the chunk section is empty then skip it
                    final LevelChunkSection levelChunkSection = levelChunk.getSection(y - level.getMinSectionY());
                    if (levelChunkSection.hasOnlyAir()) {
                        continue;
                    }
                    // TODO: Add occlusion logic here?

                    final RenderSection section = getRenderSection(x, y, z);

                    if (section == null) {
                        continue;
                    }

                    collector.visit(x, y, z);
                    if (section.getPendingUpdate() != 0) {
                        vs$shipTasks.add(section);
                    }
                }
            });

            shipRenderLists.put(ship, collector.createRenderLists(viewport));
        }

        final BlockPos cameraPos = camera.blockPosition();
        vs$shipTasks.sort(Comparator.comparingDouble(
            section -> section.getSquaredDistance(cameraPos.getX() + 0.5F, cameraPos.getY() + 0.5F, cameraPos.getZ() + 0.5F)));
    }

    @Inject(at = @At("TAIL"), method = "prepareChunkRendering")
    private void prepareShipChunkRendering(final ChunkRenderMatrices matrices, final double x, final double y,
        final double z, final boolean indexedRendering, final CallbackInfo ci) {
        SodiumCompat.prepareShips((RenderSectionManager) (Object) this, x, y, z, indexedRendering);
    }

    @Inject(at = @At("HEAD"), method = "submitDeferredSectionTasks")
    private void submitShipSectionTasks(final ChunkJobCollector collector, final UploadResourceBudget uploadBudget,
        final CallbackInfo ci) {
        final Iterator<RenderSection> tasks = vs$shipTasks.iterator();
        while (tasks.hasNext() && collector.hasBudgetRemaining() && uploadBudget.isAvailable()) {
            submitSectionTask(collector, tasks.next(), uploadBudget);
            tasks.remove();
        }
    }

    /**
     * The culling tree only knows about sections near the camera, so it would hold back every important ship rebuild.
     */
    @WrapOperation(
        method = {"submitImportantSectionTasks", "isSectionImmediatePresentationCandidate"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/SectionTree;isSectionVisible(Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;)Z"
        )
    )
    private boolean shipSectionsAreVisible(final SectionTree tree, final Viewport viewport, final RenderSection section,
        final Operation<Boolean> original) {
        if (section != null && VSGameUtilsKt.isChunkInShipyard(level, section.getChunkX(), section.getChunkZ())) {
            return true;
        }
        return original.call(tree, viewport, section);
    }

    @WrapMethod(method = "tickVisibleRenders")
    private void tickVisibleShipRenders(Operation<Void> original) {
        original.call();

        SortedRenderLists trueRenderLists = renderLists;

        for (final SortedRenderLists currentShipRenderLists : shipRenderLists.values()) {
            renderLists = currentShipRenderLists;
            original.call();
        }

        renderLists = trueRenderLists;
    }
}
