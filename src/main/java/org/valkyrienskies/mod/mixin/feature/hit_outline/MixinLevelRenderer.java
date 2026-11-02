package org.valkyrienskies.mod.mixin.feature.hit_outline;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /**
     * @reason mojank developers who wrote this don't quite understand what a matrixstack is apparently
     */
    @WrapOperation(
        method = "submitBlockOutline",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private void preRenderShape(final PoseStack matrixStack, final double x, final double y, final double z,
        final Operation<Void> original, @Local final BlockOutlineRenderState state, @Local final Vec3 cameraPos) {
        final BlockPos pos = state.pos();
        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(Minecraft.getInstance().level, pos);
        if (ship != null) {
            transformRenderWithShip(ship.getRenderTransform(), matrixStack, pos, cameraPos.x, cameraPos.y, cameraPos.z);
        } else {
            original.call(matrixStack, x, y, z);
        }
    }
}
