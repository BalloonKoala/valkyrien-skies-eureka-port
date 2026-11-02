package org.valkyrienskies.mod.mixin.feature.render_ship_debug_bb;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    /**
     * This mixin renders ship bounding boxes and center of masses.
     *
     * <p>Debug lines are gizmos in world coordinates now, so the rotated voxel box is drawn edge by edge.
     */
    @Inject(method = "emitGizmos", at = @At("HEAD"))
    private void postRender(final Frustum frustum, final double cameraX, final double cameraY, final double cameraZ,
        final float partialTicks, final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel world = minecraft.level;
        if (world == null || !minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            return;
        }
        final VsiClientShipWorld shipObjectClientWorld = VSGameUtilsKt.getShipObjectWorld(world);

        for (final ClientShip shipObjectClient : shipObjectClientWorld.getLoadedShips()) {
            final ShipTransform shipRenderTransform = shipObjectClient.getRenderTransform();
            final Vector3dc shipRenderPosition = shipRenderTransform.getShipPositionInWorldCoordinates();

            final double renderRadius = .25;
            final AABB shipCenterOfMassBox =
                new AABB(shipRenderPosition.x() - renderRadius, shipRenderPosition.y() - renderRadius,
                    shipRenderPosition.z() - renderRadius, shipRenderPosition.x() + renderRadius,
                    shipRenderPosition.y() + renderRadius, shipRenderPosition.z() + renderRadius);
            Gizmos.cuboid(shipCenterOfMassBox, GizmoStyle.stroke(0xFFFAC213));

            // Render the ship's voxel AABB
            final AABBic shipVoxelAABBic = shipObjectClient.getShipAABB();
            if (shipVoxelAABBic != null) {
                vs$voxelBox(shipRenderTransform.getShipToWorld(), shipVoxelAABBic);
            }

            // Render the ship's render AABB
            Gizmos.cuboid(VectorConversionsMCKt.toMinecraft(shipObjectClient.getRenderAABB()), GizmoStyle.stroke(0xFFEA00D9));
        }
    }

    @Unique
    private static void vs$voxelBox(final Matrix4dc shipToWorld, final AABBic box) {
        final Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            final Vector3d corner = shipToWorld.transformPosition(
                (i & 1) == 0 ? box.minX() : box.maxX(),
                (i & 2) == 0 ? box.minY() : box.maxY(),
                (i & 4) == 0 ? box.minZ() : box.maxZ(),
                new Vector3d()
            );
            corners[i] = new Vec3(corner.x, corner.y, corner.z);
        }
        for (int i = 0; i < 8; i++) {
            for (final int axis : new int[] {1, 2, 4}) {
                if ((i & axis) == 0) {
                    Gizmos.line(corners[i], corners[i | axis], 0xFFFF0000);
                }
            }
        }
    }
}
