package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRenderer;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixinducks.client.render.ChunkSectionsToRenderDuck;
import org.valkyrienskies.mod.mixinducks.client.render.LevelRendererVanillaDuck;
import org.valkyrienskies.mod.mixinducks.mod_compat.vanilla_renderer.LevelRendererDuck;

@Mixin(value = LevelRenderer.class, priority = 999)
public abstract class MixinLevelRendererVanilla implements LevelRendererDuck, LevelRendererVanillaDuck {
    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow
    private @Nullable ViewArea viewArea;

    @Shadow
    public abstract ChunkSectionsToRender prepareChunkRenders(Matrix4fc modelViewMatrix, boolean respectTranslucentOrder);

    @Shadow
    @Final
    private SectionOcclusionGraph sectionOcclusionGraph;

    /**
     * Which ship each visible ship section belongs to. Those sections sit at the front of visibleSections, so the
     * translucent pass, which walks the list backwards, draws them after the world like it always has.
     */
    @Unique
    private final Reference2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection, ClientShip> vs$shipSections =
        new Reference2ObjectOpenHashMap<>();
    @Unique
    private int vs$shipSectionCount = 0;
    @Unique
    private ClientShip vs$drawingShip = null;
    @Unique
    private int vs$sectionOffsetX;
    @Unique
    private int vs$sectionOffsetY;
    @Unique
    private int vs$sectionOffsetZ;

    /**
     * Fix the distance to render chunks, so that MC doesn't think ship chunks are too far away
     */
    @WrapOperation(
        method = "compileSections",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D")
    )
    private double includeShipChunksInNearChunks(final BlockPos from, final Vec3i to, final Operation<Double> distSqr) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            Minecraft.getInstance().level, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()
        );
    }

    @Override
    public void vs$setNeedsFrustumUpdate() {
        ((SectionOcclusionGraphAccessor) this.sectionOcclusionGraph).getNeedsFrustumUpdate().set(true);
    }

    @Inject(method = "clearVisibleSections", at = @At("HEAD"))
    private void forgetShipSections(final CallbackInfo ci) {
        vs$shipSectionCount = 0;
    }

    @Override
    public void vs$addShipVisibleChunks(final Frustum frustum) {
        // Take last frame's ship sections out first, the transforms changed since then
        if (vs$shipSectionCount != 0) {
            visibleSections.removeElements(0, vs$shipSectionCount);
            vs$shipSectionCount = 0;
        }
        vs$shipSections.clear();

        final ClientLevel level = Minecraft.getInstance().level;
        if (viewArea == null || level == null) {
            return;
        }
        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
        final ViewAreaAccessor chunkStorageAccessor = (ViewAreaAccessor) viewArea;
        final ObjectArrayList<SectionRenderDispatcher.RenderSection> found = new ObjectArrayList<>();

        for (final ClientShip shipObject : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (ShipRendererKt.getShipRenderer(shipObject) != ShipRenderer.VANILLA) {
                continue;
            }

            // Don't bother rendering the ship if its AABB isn't visible to the frustum
            if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(shipObject.getRenderAABB()))) {
                continue;
            }

            shipObject.getActiveChunksSet().forEach((x, z) -> {
                final LevelChunk levelChunk = level.getChunk(x, z);
                for (int y = level.getMinSectionY(); y <= level.getMaxSectionY(); y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final SectionRenderDispatcher.RenderSection renderChunk =
                        chunkStorageAccessor.callGetRenderSectionAt(tempPos);
                    if (renderChunk == null || vs$shipSections.containsKey(renderChunk)) {
                        continue;
                    }
                    // If the chunk section is empty then skip it
                    final LevelChunkSection levelChunkSection = levelChunk.getSection(y - level.getMinSectionY());
                    if (levelChunkSection.hasOnlyAir()) {
                        continue;
                    }

                    // If the chunk isn't in the frustum then skip it
                    final AABBd b2 = new AABBd((x << 4) - 6e-1, (y << 4) - 6e-1, (z << 4) - 6e-1,
                        (x << 4) + 15.6, (y << 4) + 15.6, (z << 4) + 15.6)
                        .transform(shipObject.getRenderTransform().getShipToWorld());

                    if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(b2))) {
                        continue;
                    }

                    vs$shipSections.put(renderChunk, shipObject);
                    found.add(renderChunk);
                }
            });
        }

        visibleSections.addAll(0, found);
        vs$shipSectionCount = found.size();
    }

    /**
     * Terrain shares one model view matrix per batch of draws, so the world and each ship are prepared as separate
     * batches. Ship sections sit at the front of visibleSections, this takes them out for the world and then gives
     * vanilla one ship at a time.
     */
    @Unique
    private ChunkSectionsToRender vs$prepareWithShips(final Matrix4fc modelView, final boolean respectTranslucentOrder,
        final Operation<ChunkSectionsToRender> original) {
        if (vs$drawingShip != null || vs$shipSectionCount == 0) {
            return original.call(modelView, respectTranslucentOrder);
        }

        final ObjectArrayList<SectionRenderDispatcher.RenderSection> allSections = new ObjectArrayList<>(visibleSections);
        final Map<ClientShip, List<SectionRenderDispatcher.RenderSection>> sectionsByShip = new LinkedHashMap<>();
        for (int i = 0; i < vs$shipSectionCount; i++) {
            final SectionRenderDispatcher.RenderSection section = allSections.get(i);
            sectionsByShip.computeIfAbsent(vs$shipSections.get(section), ship -> new ArrayList<>()).add(section);
        }

        visibleSections.removeElements(0, vs$shipSectionCount);
        final ChunkSectionsToRender world = original.call(modelView, respectTranslucentOrder);

        final List<ChunkSectionsToRender> ships = new ArrayList<>();
        sectionsByShip.forEach((ship, sections) -> {
            visibleSections.clear();
            visibleSections.addAll(sections);
            vs$drawingShip = ship;
            // Multi draw only takes one batch of section data per frame, ships use the separate draws
            ships.add(prepareChunkRenders(vs$beginShip(ship, modelView), respectTranslucentOrder));
        });
        vs$drawingShip = null;

        visibleSections.clear();
        visibleSections.addAll(allSections);
        ((ChunkSectionsToRenderDuck) world).vs$setShipRenders(ships);
        return world;
    }

    @WrapMethod(method = "prepareChunkRenders")
    private ChunkSectionsToRender vs$prepareChunkRenders(final Matrix4fc modelView, final boolean respectTranslucentOrder,
        final Operation<ChunkSectionsToRender> original) {
        return vs$prepareWithShips(modelView, respectTranslucentOrder, original);
    }

    @WrapMethod(method = "prepareChunkRendersIndirect")
    private ChunkSectionsToRender vs$prepareChunkRendersIndirect(final Matrix4fc modelView,
        final boolean respectTranslucentOrder, final Operation<ChunkSectionsToRender> original) {
        return vs$prepareWithShips(modelView, respectTranslucentOrder, original);
    }

    /**
     * The terrain shader computes Position + (ChunkPosition - CameraBlockPos) + CameraOffset, and shipyard coordinates
     * are far too big to survive that in floats. Ship sections are handed positions relative to where the camera is in
     * ship space instead, which also keeps fog distances right. The matrix is built in doubles and only the small
     * camera relative result is converted to floats.
     */
    @Unique
    private Matrix4f vs$beginShip(final ClientShip ship, final Matrix4fc modelView) {
        final Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        final Matrix4dc shipToWorld = ship.getRenderTransform().getShipToWorld();
        final Vector3d cameraInShip = ship.getRenderTransform().getWorldToShip()
            .transformPosition(camera.x, camera.y, camera.z, new Vector3d());

        final int camBlockX = Mth.floor(camera.x);
        final int camBlockY = Mth.floor(camera.y);
        final int camBlockZ = Mth.floor(camera.z);
        final int shipBlockX = Mth.floor(cameraInShip.x);
        final int shipBlockY = Mth.floor(cameraInShip.y);
        final int shipBlockZ = Mth.floor(cameraInShip.z);

        vs$sectionOffsetX = camBlockX - shipBlockX;
        vs$sectionOffsetY = camBlockY - shipBlockY;
        vs$sectionOffsetZ = camBlockZ - shipBlockZ;

        final Matrix4d shipToCamera = new Matrix4d()
            .translation(-camera.x, -camera.y, -camera.z)
            .mul(shipToWorld)
            .translate(
                shipBlockX - (camBlockX - camera.x),
                shipBlockY - (camBlockY - camera.y),
                shipBlockZ - (camBlockZ - camera.z)
            );

        return new Matrix4f(modelView).mul(new Matrix4f(shipToCamera));
    }

    @WrapOperation(
        method = "extractSectionDrawGroups",
        at = @At(value = "NEW", target = "(IIIF)Lnet/minecraft/client/renderer/DynamicGpuData$ChunkSectionInfo;")
    )
    private DynamicGpuData.ChunkSectionInfo vs$shipSectionInfo(final int x, final int y, final int z,
        final float visibility, final Operation<DynamicGpuData.ChunkSectionInfo> original) {
        if (vs$drawingShip == null) {
            return original.call(x, y, z, visibility);
        }
        return original.call(x + vs$sectionOffsetX, y + vs$sectionOffsetY, z + vs$sectionOffsetZ, visibility);
    }
}
