package org.valkyrienskies.eureka.mixin.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.eureka.mixinducks.HelmPoseDuck;

@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStateMixin implements HelmPoseDuck {

    @Unique
    private boolean vs_eureka$atHelm = false;

    @Override
    public boolean vs_eureka$isAtHelm() {
        return this.vs_eureka$atHelm;
    }

    @Override
    public void vs_eureka$setAtHelm(final boolean atHelm) {
        this.vs_eureka$atHelm = atHelm;
    }
}
