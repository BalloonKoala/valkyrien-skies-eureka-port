package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.client.render.CustomLecternRenderer;
import dan200.computercraft.shared.lectern.CustomLecternBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.phys.Vec3;

import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(CustomLecternRenderer.class)
public class MixinCustomLecternRenderer {
    @Redirect(method = "extractRenderState(Ldan200/computercraft/shared/lectern/CustomLecternBlockEntity;Ldan200/computercraft/client/render/CustomLecternRenderer$State;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"))
    public boolean render$closerThan(
        final Vec3 origin, final Position pos, final double dist,
        final CustomLecternBlockEntity lectern, final CustomLecternRenderer.State state, final float partialTicks,
        final Vec3 cameraPos, final ModelFeatureRenderer.CrumblingOverlay overlay
    ) {
        final double distSqr = VSGameUtilsKt.squaredDistanceBetweenInclShips(
            ((BlockEntity) (lectern)).getLevel(),
            origin.x,
            origin.y,
            origin.z,
            pos.x(),
            pos.y(),
            pos.z()
        );
        return distSqr < dist * dist;
    }
}
