package org.valkyrienskies.eureka.mixin.client;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.mixinducks.HelmPoseDuck;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

@Mixin(HumanoidMobRenderer.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
    private static void standUpAtHelm(final LivingEntity livingEntity, final HumanoidRenderState state,
        final float partialTick, final ItemModelResolver itemModelResolver, final CallbackInfo info) {
        if (!(livingEntity instanceof Avatar)) {
            return;
        }
        final Entity vehicle = livingEntity.getVehicle();
        ((HelmPoseDuck) state).vs_eureka$setAtHelm(vehicle instanceof ShipMountingEntity);
        if (vehicle instanceof ShipMountingEntity) {
            var pos = vehicle.position();
            if (Math.abs((pos.x + .5) % 1.0) < .05 && Math.abs((pos.z + .5) % 1.0) < .05) {
                state.isPassenger = false;
            }
        }
    }
}
