package org.valkyrienskies.mod.mixin.client.renderer;

import net.minecraft.world.level.Level;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.client.IVSCamera;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;

    // Whether the frame being drawn moved entities to where their ship is drawn, so they need putting back
    @Unique
    private boolean vs$entitiesMoved = false;
    // region Mount the camera to the ship
    @Shadow
    @Final
    private Camera mainCamera;

    // The camera and everything else read these transforms from this step onwards
    @Inject(method = "update", at = @At("HEAD"))
    private void preRender(DeltaTracker deltaTracker, CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null) {
            // Update ship render transforms
            final VsiClientShipWorld shipWorld =
                IShipObjectWorldClientProvider.class.cast(this.minecraft).getShipObjectWorld();
            if (shipWorld == null) {
                return;
            }

            final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            shipWorld.updateRenderTransforms(partialTick);

            // Without a loaded ship no entity rides or stands on one, skip going through every entity twice a frame
            vs$entitiesMoved = !shipWorld.getLoadedShips().isEmpty();
            if (!vs$entitiesMoved) return;

            // Also update entity last tick positions, so that they interpolate correctly
            for (final Entity entity : clientWorld.entitiesForRendering()) {
                if (!EntityDragger.isDraggable(entity)) {
                    continue;
                }
                // First, try getting the ship the entity is mounted to, if one exists
                final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, partialTick);

                if (shipMountedToData != null) {
                    final ClientShip shipMountedTo = (ClientShip) shipMountedToData.getShipMountedTo();
                    // If the entity is mounted to a ship then update their position
                    final Vector3dc passengerPos = shipMountedToData.getMountPosInShip();
                    final Vector3dc entityShouldBeHere = shipMountedTo.getRenderTransform().getShipToWorld()
                        .transformPosition(passengerPos, new Vector3d());
                    entity.setPos(entityShouldBeHere.x(), entityShouldBeHere.y(), entityShouldBeHere.z());
                    entity.xo = entityShouldBeHere.x();
                    entity.yo = entityShouldBeHere.y();
                    entity.zo = entityShouldBeHere.z();
                    entity.xOld = entityShouldBeHere.x();
                    entity.yOld = entityShouldBeHere.y();
                    entity.zOld = entityShouldBeHere.z();
                    continue;
                }

                final EntityDraggingInformation entityDraggingInformation =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                final Long renderShipId = entityDraggingInformation.getRenderShipId();
                final ClientShip shipObject = renderShipId == null ? null :
                    VSGameUtilsKt.getShipObjectWorld(clientWorld).getLoadedShips().getById(renderShipId);
                // Don't run this if [partialTick] is too close to 1, getting that close to dividing by 0 could mess
                // something up
                if (shipObject != null && partialTick < .99999) {
                    if (!entityDraggingInformation.getRestoreCachedLastPosition()) {
                        entityDraggingInformation.setCachedLastPosition(new Vector3d(entity.xo, entity.yo, entity.zo));
                        entityDraggingInformation.setCachedOldPosition(new Vector3d(entity.xOld, entity.yOld, entity.zOld));
                        entityDraggingInformation.setRestoreCachedLastPosition(true);
                    }

                    // Interpolate along the deck, then take that to wherever the ship is drawn this frame
                    final Vector3dc entityShouldBeHere = shipObject.getRenderTransform().getShipToWorld().transformPosition(
                        entityDraggingInformation.getRenderPrevPosOnShip()
                            .lerp(entityDraggingInformation.getRenderPosOnShip(), partialTick, new Vector3d()));

                    // Update the entity last tick positions such that the entity's render position will be
                    // interpolated to be [entityShouldBeHere]. The camera reads xo and the entity renderer reads xOld.
                    entity.xo = entity.xOld = (entityShouldBeHere.x() - (entity.getX() * partialTick)) / (1.0 - partialTick);
                    entity.yo = entity.yOld = (entityShouldBeHere.y() - (entity.getY() * partialTick)) / (1.0 - partialTick);
                    entity.zo = entity.zOld = (entityShouldBeHere.z() - (entity.getZ() * partialTick)) / (1.0 - partialTick);
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void postRender(CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null && vs$entitiesMoved) {
            vs$entitiesMoved = false;
            // Restore the entity last tick positions that were replaced during this frame
            for (final Entity entity : clientWorld.entitiesForRendering()) {
                final EntityDraggingInformation vsEntity =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                if (vsEntity.getRestoreCachedLastPosition()) {
                    vsEntity.setRestoreCachedLastPosition(false);
                    final Vector3dc cachedLastPosition = vsEntity.getCachedLastPosition();
                    final Vector3dc cachedOldPosition = vsEntity.getCachedOldPosition();
                    if (cachedLastPosition != null && cachedOldPosition != null) {
                        entity.xo = cachedLastPosition.x();
                        entity.yo = cachedLastPosition.y();
                        entity.zo = cachedLastPosition.z();
                        entity.xOld = cachedOldPosition.x();
                        entity.yOld = cachedOldPosition.y();
                        entity.zOld = cachedOldPosition.z();
                    } else {
                        System.err.println("How was cachedLastPosition was null?");
                    }
                }
            }
        }
    }

    // endregion
}
