package org.valkyrienskies.mod.compat;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import java.util.Iterator;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.oit.OitStage;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ShipRenderEventSodium;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.mod_compat.sodium.UniformBufferManagerAccessor;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.DefaultChunkRendererDuck;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

public class SodiumCompat {

    public static void onChunkAdded(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    public static void onChunkRemoved(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    public static void vsRenderLayer(RenderSectionManager renderSectionManager, UniformBufferManager uniformBufferManager, ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z,
            FogParameters fogParameters, GpuSampler terrainSampler, RenderPass renderPass, @Nullable OitStage stage, boolean indexedRendering) {

        VSGameEvents.INSTANCE.getShipsStartRenderingSodium().emit(new VSGameEvents.ShipStartRenderEventSodium(
            pass, matrices, x, y, z
        ));

        // Sodium writes its uniforms once per frame, so each ship needs its own write and the world's has to come back
        final UniformBufferManagerAccessor uniforms = (UniformBufferManagerAccessor) uniformBufferManager;
        final GpuBufferSlice worldUniforms = uniformBufferManager.getUniformBuffer();

        ((RenderSectionManagerDuck) renderSectionManager).vs_getShipRenderLists().forEach((ship, renderList) -> {
            VSGameEvents.INSTANCE.getRenderShipSodium().emit(new ShipRenderEventSodium(pass, matrices, x, y, z, ship, renderList));
            final ShipTransform shipTransform = ship.getRenderTransform();

            final float distanceScaling = 1 / (float) shipTransform.getShipToWorldScaling().x();
            final FogParameters shipFog = distanceScaling == 1f ? fogParameters : new FogParameters(
                fogParameters.red(), fogParameters.green(), fogParameters.blue(), fogParameters.alpha(),
                fogParameters.environmentalStart() * distanceScaling, fogParameters.environmentalEnd() * distanceScaling,
                fogParameters.renderStart() * distanceScaling, fogParameters.renderEnd() * distanceScaling,
                fogParameters.cullDistance() * distanceScaling
            );

            final Vector3dc cameraShipSpace = shipTransform.getWorldToShip().transformPosition(new Vector3d(x, y, z));
            final Matrix4dc s = ship.getRenderTransform().getShipToWorld();
            final Matrix4d newModelView = new Matrix4d(matrices.modelView())
                .translate(-x, -y, -z)
                .mul(s)
                .translate(cameraShipSpace);

            final ChunkRenderMatrices newMatrices =
                new ChunkRenderMatrices(matrices.projection(), new Matrix4f(newModelView));

            uniforms.setHasUpdatedThisFrame(false);
            uniformBufferManager.update(newMatrices, shipFog);

            renderSectionManager.getChunkRenderer().render(newMatrices, renderList, pass,
                new CameraTransform(cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z()), shipFog,
                indexedRendering, renderPass, terrainSampler, uniformBufferManager.getUniformBuffer(),
                uniformBufferManager.getSectionTimeInfo(), stage);

            VSGameEvents.INSTANCE.getPostRenderShipSodium().emit(new ShipRenderEventSodium(pass, matrices, x, y, z, ship, renderList));
        });

        uniforms.setHasUpdatedThisFrame(true);
        uniforms.setUniformData(worldUniforms);
    }

    /**
     * Draw commands are built before the render pass starts. Block face culling depends on where the camera is, and
     * it moves relative to a ship all the time, so ship commands are rebuilt each frame.
     */
    public static void prepareShips(RenderSectionManager renderSectionManager, double x, double y, double z, boolean indexedRendering) {
        final DefaultChunkRendererDuck renderer = (DefaultChunkRendererDuck) renderSectionManager.getChunkRenderer();
        ((RenderSectionManagerDuck) renderSectionManager).vs_getShipRenderLists().forEach((ship, renderList) -> {
            for (final Iterator<ChunkRenderList> lists = renderList.iterator(); lists.hasNext(); ) {
                lists.next().getRegion().clearAllCachedBatches();
            }
            final Vector3dc cameraShipSpace = ship.getRenderTransform().getWorldToShip().transformPosition(new Vector3d(x, y, z));
            renderer.vs$prepareAlongside(renderList, new CameraTransform(cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z()), indexedRendering);
        });
    }
}
