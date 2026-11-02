package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.render.EntityRenderStateDuck;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    /**
     * Entities are rendered along with their name tags, which is good as the ship transforms are already applied.
     * For readability, however, we want the tags to always be vertically oriented so we negate rotation of the ship.
     * The tag is moved to its spot before the rotation is undone, as offsetting it vertically in shipspace makes more
     * sense. The renderer adds half a block up on its own, so that part is taken back out.
     */
    @WrapOperation(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
        )
    )
    private void revertShipRotation(final SubmitNodeCollector collector, final PoseStack poseStack, final Vec3 attachment,
        final int i, final Component component, final boolean bl, final int j,
        final CameraRenderState camera, final Operation<Void> original,
        @Local(argsOnly = true) final EntityRenderState state) {
        final Entity entity = ((EntityRenderStateDuck) state).vs$getRenderedEntity();
        final ClientShip ship = entity == null ? null
            : (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.level(), entity.blockPosition());
        if (ship == null || attachment == null) {
            original.call(collector, poseStack, attachment, i, component, bl, j, camera);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z);
        poseStack.rotate(new Quaternionf(ship.getRenderTransform().getShipToWorldRotation()).invert());
        original.call(collector, poseStack, new Vec3(0.0, -0.5, 0.0), i, component, bl, j, camera);
        poseStack.popPose();
    }
}
