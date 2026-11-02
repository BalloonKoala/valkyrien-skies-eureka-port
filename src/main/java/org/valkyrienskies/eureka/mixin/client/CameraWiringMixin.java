package org.valkyrienskies.eureka.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.gui.wiring.WiringCamera;
import org.valkyrienskies.mod.client.IVSCamera;

@Mixin(Camera.class)
public abstract class CameraWiringMixin {

    @Inject(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER)
    )
    private void flyAroundShip(final CallbackInfo info) {
        WiringCamera.apply((IVSCamera) this);
    }
}
