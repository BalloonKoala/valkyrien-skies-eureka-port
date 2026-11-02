package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.sugar.Local;
import org.valkyrienskies.mod.mixinducks.client.render.EntityRenderStateDuck;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = EntityRenderDispatcher.class, priority = 500)
public class MixinEntityRenderDispatcher {

    @Shadow
    public Camera camera;

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void preDistanceToSqr(final Entity entity, final CallbackInfoReturnable<Double> cir) {
        final Vec3 pos = entity.position();
        // entity seems to be null sometimes when the "real camera" mod is used
        if (entity == null) return;
        cir.setReturnValue(VSGameUtilsKt.squaredDistanceToInclShips(entity, pos.x, pos.y, pos.z));
    }

    /**
     * The render state doesn't carry the entity, remember it for the submit below.
     */
    @ModifyReturnValue(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> EntityRenderState rememberEntity(final EntityRenderState state, final E entity,
        final float partialTicks) {
        ((EntityRenderStateDuck) state).vs$setRenderedEntity(entity, partialTicks);
        return state;
    }

    @Inject(method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
        ))
    private <S extends EntityRenderState> void render(
        final S state, final CameraRenderState cameraRenderState, final double x, final double y, final double z,
        final PoseStack matrixStack, final SubmitNodeCollector collector, final CallbackInfo ci,
        @Local final Vec3 offset
    ) {
        final EntityRenderStateDuck duck = (EntityRenderStateDuck) state;
        final Entity entity = duck.vs$getRenderedEntity();
        if (entity == null) {
            return;
        }
        final float partialTicks = duck.vs$getRenderedPartialTicks();
        final float rotationYaw = entity.getYRot(partialTicks);
        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, partialTicks);

        if (shipMountedToData != null) {
            // Remove the earlier applied translation
            matrixStack.popPose();
            matrixStack.pushPose();

            final ShipTransform renderTransform = ((ClientShip) shipMountedToData.getShipMountedTo()).getRenderTransform();

            final Vec3 entityPosition = entity.getPosition(partialTicks);
            final Vector3dc transformed = renderTransform.getShipToWorld().transformPosition(shipMountedToData.getMountPosInShip(), new Vector3d());

            final double camX = x - entityPosition.x;
            final double camY = y - entityPosition.y;
            final double camZ = z - entityPosition.z;

            final Vector3dc scale = renderTransform.getShipToWorldScaling();

            matrixStack.translate(transformed.x() + camX, transformed.y() + camY, transformed.z() + camZ);
            matrixStack.rotate(new Quaternionf(renderTransform.getShipToWorldRotation()));
            matrixStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
            matrixStack.translate(offset.x, offset.y, offset.z);
        } else {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.level(), entity.blockPosition());
            if (ship != null) {
                // Remove the earlier applied translation
                matrixStack.popPose();
                matrixStack.pushPose();

                VSEntityManager.INSTANCE.getHandler(entity)
                    .applyRenderTransform(ship, entity, offset, x, y, z,
                        rotationYaw, partialTicks, matrixStack);
            } else if (entity.isPassenger()) {
                final ClientShip vehicleShip =
                    (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.level(),
                        entity.getVehicle().blockPosition());
                // If the entity is a passenger and that vehicle is in ship space
                if (vehicleShip != null) {
                    VSEntityManager.INSTANCE.getHandler(entity.getVehicle())
                        .applyRenderOnMountedEntity(vehicleShip, entity.getVehicle(), entity, partialTicks,
                            matrixStack);
                }
            }
        }
    }

    @ModifyReturnValue(
        method = "shouldRender",
        at = @At("RETURN")
    )
    boolean shouldRender(final boolean returns, final Entity entity, final Frustum frustum,
        final double camX, final double camY, final double camZ) {

        if (!returns) {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.level(), entity.blockPosition());
            if (ship != null) {
                AABB aABB = entity.getBoundingBox().inflate(0.5);
                if (aABB.hasNaN() || aABB.getSize() == 0.0) {
                    aABB = new AABB(entity.getX() - 2.0, entity.getY() - 2.0,
                        entity.getZ() - 2.0, entity.getX() + 2.0,
                        entity.getY() + 2.0, entity.getZ() + 2.0);
                }
                final AABBd aabb = VectorConversionsMCKt.toJOML(aABB);

                // Get the in world position and do it minus what the aabb already has and then add the offset
                aabb.transform(ship.getRenderTransform().getShipToWorld());
                return frustum.isVisible(VectorConversionsMCKt.toMinecraft(aabb));
            }
        }

        return returns;
    }

}
