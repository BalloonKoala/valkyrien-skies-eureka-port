package org.valkyrienskies.mod.fabric.mixin.client.render;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    /**
     * This mixin makes block damage render on ships. The decal generator reads this pose right after, so only the
     * transform has to change now.
     */
    @WrapOperation(
        method = "submitBlockDestroyAnimation",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private void renderBlockDamage(final PoseStack poseStack, final double x, final double y, final double z,
        final Operation<Void> original, @Local final BlockPos blockPos, @Local(ordinal = 0) final double camX,
        @Local(ordinal = 1) final double camY, @Local(ordinal = 2) final double camZ) {

        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(Minecraft.getInstance().level, blockPos);
        if (ship != null) {
            // Add the VS render transform
            transformRenderWithShip(ship.getRenderTransform(), poseStack, blockPos, camX, camY, camZ);
        } else {
            // Vanilla behavior
            original.call(poseStack, x, y, z);
        }
    }
}
