package org.valkyrienskies.mod.mixin.client.renderer;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.valkyrienskies.core.api.ships.ClientShip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    /**
     * This mixin makes {@link BlockEntity} in the ship render in the correct place.
     */
    @WrapOperation(
        method = "submitBlockEntities",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private void renderShipChunkBlockEntity(final PoseStack poseStack, final double x, final double y, final double z,
        final Operation<Void> original, @Local final BlockPos blockPos, @Local(ordinal = 0) final double camX,
        @Local(ordinal = 1) final double camY, @Local(ordinal = 2) final double camZ) {

        final ClientShip shipObject = VSGameUtilsKt.getLoadedShipManagingPos(Minecraft.getInstance().level, blockPos);
        if (shipObject != null) {
            transformRenderWithShip(shipObject.getRenderTransform(), poseStack, blockPos, camX, camY, camZ);
        } else {
            original.call(poseStack, x, y, z);
        }
    }
}
