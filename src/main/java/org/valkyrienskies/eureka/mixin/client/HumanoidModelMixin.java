package org.valkyrienskies.eureka.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.mixinducks.HelmPoseDuck;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Unique
    private static final float HANDS_ON_HELM_PITCH = -1.0F;

    @Unique
    private static final float HANDS_ON_HELM_YAW = 0.25F;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void handsOnHelm(final HumanoidRenderState state, final CallbackInfo info) {
        if (((HelmPoseDuck) state).vs_eureka$isAtHelm()) {
            this.rightArm.setRotation(HANDS_ON_HELM_PITCH, -HANDS_ON_HELM_YAW, 0.0F);
            this.leftArm.setRotation(HANDS_ON_HELM_PITCH, HANDS_ON_HELM_YAW, 0.0F);
        }
    }
}
