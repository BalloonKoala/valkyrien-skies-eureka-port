package org.valkyrienskies.mod.mixin.feature.world_border;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    @Inject(
        at = @At("RETURN"),
        method = "<init>"
    )
    private void setWorldBorderLevel(final CallbackInfo ci) {
        final ClientLevel self = (ClientLevel) (Object) this;
        ((OfLevel) self.getWorldBorder()).setLevel(self);
    }
}
